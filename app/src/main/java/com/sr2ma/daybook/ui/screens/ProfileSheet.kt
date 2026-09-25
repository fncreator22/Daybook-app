package com.sr2ma.daybook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sr2ma.daybook.R
import com.sr2ma.daybook.sync.SyncPreferences

/**
 * Offline Profile & Multi-Email Manager modal bottom sheet.
 *
 * Allows user to:
 * 1. Maintain First Name, Last Name, and Primary Email offline.
 * 2. Add multiple sub-email addresses to connect multiple Gmails.
 * 3. Verify emails using a 6-digit OTP verification flow.
 * 4. Persist verified status in encrypted preferences so AI & sync features
 *    operate automatically without repeated authentication.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(
    syncPrefs: SyncPreferences,
    onProfileUpdated: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var firstName by remember { mutableStateOf(syncPrefs.profileFirstName ?: "") }
    var lastName by remember { mutableStateOf(syncPrefs.profileLastName ?: "") }
    var primaryEmail by remember { mutableStateOf(syncPrefs.profilePrimaryEmail ?: "") }

    var subEmails by remember { mutableStateOf(syncPrefs.profileSubEmails.toList()) }
    var verifiedEmails by remember { mutableStateOf(syncPrefs.verifiedEmails) }

    var newEmailText by remember { mutableStateOf("") }
    var newEmailError by remember { mutableStateOf<String?>(null) }

    // OTP verification dialog state
    var verifyingEmail by remember { mutableStateOf<String?>(null) }
    var currentOtpCode by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "User Profile & Accounts",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "100% Offline Profile & Multi-Email Access",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.action_cancel),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Basic Credentials ─────────────────────────────────────────────
            Text(
                text = "BASIC CREDENTIALS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = { Text("First Name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = { Text("Last Name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(10.dp))

            // Primary Email with verification status
            val isPrimaryVerified = syncPrefs.isEmailVerified(primaryEmail)
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = primaryEmail,
                    onValueChange = { primaryEmail = it },
                    label = { Text("Primary Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (primaryEmail.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isPrimaryVerified) Color(0xFFE6F4EA) else MaterialTheme.colorScheme.errorContainer,
                        ) {
                            Text(
                                text = if (isPrimaryVerified) "VERIFIED" else "UNVERIFIED",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isPrimaryVerified) Color(0xFF137333) else MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }

                        if (!isPrimaryVerified) {
                            TextButton(
                                onClick = {
                                    val otp = syncPrefs.generateOtp(primaryEmail)
                                    verifyingEmail = primaryEmail
                                    currentOtpCode = otp
                                }
                            ) {
                                Text("Send OTP to Verify")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // ── Multi-Email Section (Gmails) ──────────────────────────────────
            Text(
                text = "CONNECTED ACCOUNTS (MULTI-GMAIL)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Add multiple email addresses to allow the AI to extract action items, sync meetings, and gather information across your accounts. Each account requires OTP verification before access is granted.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            )

            if (subEmails.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Text(
                        text = "No sub-emails added yet. Add secondary Gmails below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    subEmails.forEach { email ->
                        val verified = syncPrefs.isEmailVerified(email)
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = email,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (verified) Color(0xFFE6F4EA) else MaterialTheme.colorScheme.errorContainer,
                                    ) {
                                        Text(
                                            text = if (verified) "VERIFIED" else "PENDING OTP",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (verified) Color(0xFF137333) else MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (!verified) {
                                        TextButton(
                                            onClick = {
                                                val otp = syncPrefs.generateOtp(email)
                                                verifyingEmail = email
                                                currentOtpCode = otp
                                            }
                                        ) {
                                            Text("Verify")
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            syncPrefs.removeSubEmail(email)
                                            subEmails = syncPrefs.profileSubEmails.toList()
                                            verifiedEmails = syncPrefs.verifiedEmails
                                            onProfileUpdated()
                                        }
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_delete),
                                            contentDescription = "Remove",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Add new sub-email row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = newEmailText,
                    onValueChange = {
                        newEmailText = it
                        newEmailError = null
                    },
                    placeholder = { Text("name@gmail.com") },
                    singleLine = true,
                    isError = newEmailError != null,
                    supportingText = newEmailError?.let { { Text(it) } },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        val trimmed = newEmailText.trim().lowercase()
                        if (trimmed.isBlank() || !trimmed.contains("@")) {
                            newEmailError = "Enter valid email"
                        } else if (trimmed == primaryEmail.lowercase() || subEmails.contains(trimmed)) {
                            newEmailError = "Email already added"
                        } else {
                            syncPrefs.addSubEmail(trimmed)
                            subEmails = syncPrefs.profileSubEmails.toList()
                            newEmailText = ""
                            newEmailError = null
                            onProfileUpdated()
                        }
                    },
                ) {
                    Text("Add")
                }
            }

            Spacer(Modifier.height(24.dp))

            // Save profile button
            Button(
                onClick = {
                    syncPrefs.profileFirstName = firstName.trim().ifBlank { null }
                    syncPrefs.profileLastName = lastName.trim().ifBlank { null }
                    val fullName = listOf(firstName.trim(), lastName.trim()).filter { it.isNotBlank() }.joinToString(" ")
                    syncPrefs.userName = fullName.ifBlank { null }
                    if (primaryEmail.isNotBlank()) {
                        syncPrefs.profilePrimaryEmail = primaryEmail.trim().lowercase()
                    }
                    onProfileUpdated()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save Profile & Credentials")
            }
        }
    }

    // ── OTP Verification Modal ────────────────────────────────────────────────
    verifyingEmail?.let { emailToVerify ->
        OtpVerificationDialog(
            email = emailToVerify,
            generatedOtp = currentOtpCode ?: "",
            onVerify = { enteredOtp ->
                val success = syncPrefs.verifyOtp(emailToVerify, enteredOtp)
                if (success) {
                    verifiedEmails = syncPrefs.verifiedEmails
                    verifyingEmail = null
                    currentOtpCode = null
                    onProfileUpdated()
                }
                success
            },
            onResend = {
                val newOtp = syncPrefs.generateOtp(emailToVerify)
                currentOtpCode = newOtp
            },
            onDismiss = {
                verifyingEmail = null
                currentOtpCode = null
            },
        )
    }
}

/**
 * 6-digit OTP verification dialog.
 * Prompts user to paste or enter the OTP received for the email.
 * Includes simulated code display for offline convenience.
 */
@Composable
fun OtpVerificationDialog(
    email: String,
    generatedOtp: String,
    onVerify: (String) -> Boolean,
    onResend: () -> Unit,
    onDismiss: () -> Unit,
) {
    var enteredOtp by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Email OTP Verification",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "A 6-digit verification code has been generated for $email.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                // Offline simulation helper card
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "OTP Generated (Offline Simulation):",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = generatedOtp,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 3.sp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            TextButton(onClick = { enteredOtp = generatedOtp }) {
                                Text("Auto-Fill")
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = enteredOtp,
                    onValueChange = {
                        if (it.length <= 6) {
                            enteredOtp = it.filter { char -> char.isDigit() }
                            isError = false
                            errorMessage = null
                        }
                    },
                    label = { Text("Enter 6-Digit OTP") },
                    placeholder = { Text("123456") },
                    singleLine = true,
                    isError = isError,
                    supportingText = errorMessage?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onResend) {
                        Text("Resend Code")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (enteredOtp.length < 6) {
                        isError = true
                        errorMessage = "Please enter 6 digits"
                    } else {
                        val verified = onVerify(enteredOtp)
                        if (!verified) {
                            isError = true
                            errorMessage = "Invalid or expired OTP code"
                        }
                    }
                },
            ) {
                Text("Verify Account")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
