package com.sr2ma.daybook.domain

import com.sr2ma.daybook.data.DaybookDatabase
import com.sr2ma.daybook.domain.model.Task
import com.sr2ma.daybook.domain.model.Meeting
import com.sr2ma.daybook.domain.model.LogEntry

/**
 * OKF-inspired Knowledge Graph Engine for Daybook.
 *
 * Inspired by Google Cloud's Open Knowledge Format (June 2026):
 * https://github.com/GoogleCloudPlatform/open-knowledge-format
 *
 * Philosophy: rather than RAG (vector similarity → predict next word), OKF connects
 * information "dot to dot" via explicit named relationships. Given an anchor entity,
 * we traverse the graph 1–2 hops to surface contextually connected items.
 *
 * Storage: two SQLCipher tables — `kg_nodes` and `kg_edges` — added in migration v8.
 * Uses the sqlite-vec KNN index (already in project) as the anchor lookup.
 *
 * This is a pure Kotlin object — no Android, no I/O at call site.
 * All DB work is on the caller's coroutine dispatcher.
 */
object KnowledgeGraphEngine {

    // ── Node / Edge data classes ──────────────────────────────────────────────

    data class KgNode(
        val id: Long,
        val entityText: String,
        val sourceId: Long,
        val sourceType: String,   // "task" | "meeting" | "log" | "pass"
        val lastSeen: Long,
    )

    data class KgEdge(
        val fromNode: Long,
        val toNode: Long,
        val relation: String,     // "mentions" | "follows" | "related_to" | "blocks"
        val weight: Double,
    )

    // Relation constants — deterministic, no LLM
    const val RELATION_MENTIONS     = "mentions"      // entity A names entity B
    const val RELATION_FOLLOWS      = "follows"       // A created after B completed
    const val RELATION_RELATED_TO   = "related_to"    // same-day co-occurrence
    const val RELATION_BLOCKS       = "blocks"        // A has dependency on B (future)

    // ── Write operations ──────────────────────────────────────────────────────

    /**
     * Upsert a node. Returns the node ID (new or existing).
     * Thread-safe: caller must pass a writable DB handle on an IO dispatcher.
     */
    fun upsertNode(
        db: DaybookDatabase,
        entityText: String,
        sourceId: Long,
        sourceType: String,
    ): Long {
        val wdb = db.writableDatabase
        val nowSec = System.currentTimeMillis() / 1000L

        // Check for existing node (same entity_text + source combination)
        val existing = wdb.rawQuery(
            "SELECT id FROM kg_nodes WHERE entity_text = ? AND source_id = ? AND source_type = ?",
            arrayOf(entityText, sourceId.toString(), sourceType),
        )
        val existingId = if (existing.moveToFirst()) existing.getLong(0) else -1L
        existing.close()

        if (existingId != -1L) {
            wdb.execSQL(
                "UPDATE kg_nodes SET last_seen = ? WHERE id = ?",
                arrayOf(nowSec, existingId),
            )
            return existingId
        }

        wdb.execSQL(
            "INSERT INTO kg_nodes (entity_text, source_id, source_type, last_seen) VALUES (?, ?, ?, ?)",
            arrayOf<Any?>(entityText, sourceId, sourceType, nowSec),
        )
        val cursor = wdb.rawQuery("SELECT last_insert_rowid()", null)
        val newId = if (cursor.moveToFirst()) cursor.getLong(0) else -1L
        cursor.close()
        return newId
    }

    /**
     * Upsert an edge between two nodes. If the edge already exists, updates weight
     * with exponential moving average (decay factor 0.8) to prevent stale boosting.
     */
    fun upsertEdge(
        db: DaybookDatabase,
        fromNode: Long,
        toNode: Long,
        relation: String,
        weight: Double = 1.0,
    ) {
        val wdb = db.writableDatabase
        val existing = wdb.rawQuery(
            "SELECT weight FROM kg_edges WHERE from_node = ? AND to_node = ? AND relation = ?",
            arrayOf(fromNode.toString(), toNode.toString(), relation),
        )
        if (existing.moveToFirst()) {
            val oldWeight = existing.getDouble(0)
            val newWeight = 0.8 * oldWeight + 0.2 * weight  // EMA
            wdb.execSQL(
                "UPDATE kg_edges SET weight = ? WHERE from_node = ? AND to_node = ? AND relation = ?",
                arrayOf<Any?>(newWeight, fromNode, toNode, relation),
            )
        } else {
            wdb.execSQL(
                "INSERT INTO kg_edges (from_node, to_node, relation, weight) VALUES (?, ?, ?, ?)",
                arrayOf<Any?>(fromNode, toNode, relation, weight),
            )
        }
        existing.close()
    }

    // ── Read operations ───────────────────────────────────────────────────────

    /**
     * Graph expansion: given a list of anchor node IDs, traverse up to [maxHops]
     * hops of edges with weight > [minWeight], returning all reachable nodes.
     *
     * Uses a recursive CTE — sqlite supports this natively and it runs entirely
     * inside SQLCipher without any extra library.
     */
    fun expand(
        db: DaybookDatabase,
        anchorIds: List<Long>,
        maxHops: Int = 2,
        minWeight: Double = 0.3,
    ): List<KgNode> {
        if (anchorIds.isEmpty()) return emptyList()
        val rdb = db.readableDatabase
        val anchorsJoined = anchorIds.joinToString(",")
        val cursor = rdb.rawQuery(
            """
            WITH RECURSIVE graph(node_id, hops) AS (
                SELECT id, 0 FROM kg_nodes WHERE id IN ($anchorsJoined)
                UNION
                SELECT e.to_node, g.hops + 1
                FROM kg_edges e
                JOIN graph g ON e.from_node = g.node_id
                WHERE g.hops < ? AND e.weight >= ?
            )
            SELECT DISTINCT n.id, n.entity_text, n.source_id, n.source_type, n.last_seen
            FROM graph g
            JOIN kg_nodes n ON n.id = g.node_id
            WHERE n.id NOT IN ($anchorsJoined)
            ORDER BY g.hops ASC
            """.trimIndent(),
            arrayOf(maxHops.toString(), minWeight.toString()),
        )
        val result = mutableListOf<KgNode>()
        while (cursor.moveToNext()) {
            result += KgNode(
                id         = cursor.getLong(0),
                entityText = cursor.getString(1),
                sourceId   = cursor.getLong(2),
                sourceType = cursor.getString(3),
                lastSeen   = cursor.getLong(4),
            )
        }
        cursor.close()
        return result
    }

    /**
     * Finds nodes whose entity_text is mentioned in [text], and performs graph expansion
     * to surface 1-2 hop related context.
     */
    fun expandFromText(
        db: DaybookDatabase,
        text: String,
        maxHops: Int = 2,
        limit: Int = 6,
    ): List<KgNode> {
        if (text.isBlank()) return emptyList()
        val rdb = db.readableDatabase
        val cursor = rdb.rawQuery(
            "SELECT id, entity_text, source_id, source_type, last_seen FROM kg_nodes ORDER BY last_seen DESC LIMIT 100",
            null,
        )
        val matchingNodes = mutableListOf<KgNode>()
        val lowerText = text.lowercase()
        while (cursor.moveToNext()) {
            val id = cursor.getLong(0)
            val entityText = cursor.getString(1)
            val sourceId = cursor.getLong(2)
            val sourceType = cursor.getString(3)
            val lastSeen = cursor.getLong(4)
            if (entityText.length >= 3 && lowerText.contains(entityText.lowercase())) {
                matchingNodes.add(KgNode(id, entityText, sourceId, sourceType, lastSeen))
            }
        }
        cursor.close()

        if (matchingNodes.isEmpty()) return emptyList()
        val expanded = expand(db, matchingNodes.map { it.id }, maxHops = maxHops)
        return (matchingNodes + expanded).distinctBy { it.id }.take(limit)
    }

    /**
     * Formats a list of nodes and their connected edges into an Open Knowledge Format summary.
     */
    fun toOkfTriples(db: DaybookDatabase, nodes: List<KgNode>): String {
        if (nodes.isEmpty()) return ""
        val nodeIds = nodes.map { it.id }.joinToString(",")
        val rdb = db.readableDatabase
        val cursor = rdb.rawQuery(
            """
            SELECT n1.entity_text, e.relation, n2.entity_text
            FROM kg_edges e
            JOIN kg_nodes n1 ON e.from_node = n1.id
            JOIN kg_nodes n2 ON e.to_node = n2.id
            WHERE e.from_node IN ($nodeIds) OR e.to_node IN ($nodeIds)
            LIMIT 10
            """.trimIndent(),
            null,
        )
        val triples = mutableListOf<String>()
        while (cursor.moveToNext()) {
            triples.add("(:${cursor.getString(0)} -[${cursor.getString(1)}]-> :${cursor.getString(2)})")
        }
        cursor.close()
        val entities = nodes.joinToString(", ") { "${it.entityText} [${it.sourceType}]" }
        return buildString {
            append("OKF Knowledge Graph: Entities: ").append(entities)
            if (triples.isNotEmpty()) {
                append(". Relationships: ").append(triples.joinToString(", "))
            }
        }
    }

    // ── Population from domain data ───────────────────────────────────────────

    /**
     * Deterministic edge generation from the current day's board data.
     * Called by NightlyAgentWorker. Runs on an IO dispatcher.
     *
     * Rules (no LLM needed):
     *  - Tasks on the same day → RELATED_TO (weight 0.5)
     *  - Task title appears in log body → MENTIONS (weight 0.8)
     *  - Meeting attendee name appears in task title → MENTIONS (weight 0.75)
     */
    fun populateFromBoard(
        db: DaybookDatabase,
        tasks: List<Task>,
        meetings: List<Meeting>,
        logEntries: List<LogEntry>,
    ) {
        // 1. Upsert nodes for all tasks + meetings + logs
        val taskNodeIds = tasks.associate { task ->
            task.id to upsertNode(db, task.title, task.id, "task")
        }
        val meetingNodeIds = meetings.associate { meeting ->
            meeting.id to upsertNode(db, meeting.title, meeting.id, "meeting")
        }
        val logNodeIds = logEntries.associate { log ->
            log.id to upsertNode(db, log.body.take(80), log.id, "log")
        }

        // 2. Same-day tasks → RELATED_TO
        val tasksByDay = tasks.groupBy { it.dueDate }
        for ((_, dayTasks) in tasksByDay) {
            if (dayTasks.size < 2) continue
            for (i in dayTasks.indices) {
                for (j in i + 1 until dayTasks.size) {
                    val a = taskNodeIds[dayTasks[i].id] ?: continue
                    val b = taskNodeIds[dayTasks[j].id] ?: continue
                    upsertEdge(db, a, b, RELATION_RELATED_TO, 0.5)
                    upsertEdge(db, b, a, RELATION_RELATED_TO, 0.5)
                }
            }
        }

        // 3. Task title appears in log body → MENTIONS
        for (log in logEntries) {
            val logNodeId = logNodeIds[log.id] ?: continue
            for (task in tasks) {
                if (task.title.length > 3 && log.body.contains(task.title, ignoreCase = true)) {
                    val taskNodeId = taskNodeIds[task.id] ?: continue
                    upsertEdge(db, logNodeId, taskNodeId, RELATION_MENTIONS, 0.8)
                }
            }
        }

        // 4. Meeting attendee name appears in task title → MENTIONS
        for (meeting in meetings) {
            val meetingNodeId = meetingNodeIds[meeting.id] ?: continue
            val attendeeNames = meeting.attendees
                .split(",", ";")
                .map { it.trim() }
                .filter { it.length > 2 }
            for (attendee in attendeeNames) {
                for (task in tasks) {
                    if (task.title.contains(attendee, ignoreCase = true)) {
                        val taskNodeId = taskNodeIds[task.id] ?: continue
                        upsertEdge(db, meetingNodeId, taskNodeId, RELATION_MENTIONS, 0.75)
                    }
                }
            }
        }
    }

    // ── Maintenance ───────────────────────────────────────────────────────────

    /**
     * Remove stale edges (weight < 0.3 AND last_seen > 90 days).
     * Called by NightlyAgentWorker to keep the graph lean.
     */
    fun pruneStaleEdges(db: DaybookDatabase) {
        val cutoffSec = System.currentTimeMillis() / 1000L - 90L * 24 * 3600
        db.writableDatabase.execSQL(
            """
            DELETE FROM kg_edges
            WHERE weight < 0.3
            AND from_node IN (
                SELECT id FROM kg_nodes WHERE last_seen < ?
            )
            """.trimIndent(),
            arrayOf(cutoffSec),
        )
    }
}
