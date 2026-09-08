package com.keltruc.mymemos.ui.signin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keltruc.mymemos.R

@Composable
fun SignInScreen(viewModel: SignInViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spacer(Modifier.height(32.dp))
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(72.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Notes,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(18.dp),
                    )
                }
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
                Text(
                    stringResource(R.string.signin_title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.serverUrl,
                    onValueChange = viewModel::onServerUrl,
                    label = { Text(stringResource(R.string.signin_server_url)) },
                    placeholder = { Text("https://memos.example.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { viewModel.probeServer() }),
                    supportingText = {
                        when {
                            state.probing -> Text(stringResource(R.string.signin_probe_hint))
                            state.serverVersion != null -> Text("Memos ${state.serverVersion}")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SignInMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = state.mode == mode,
                            onClick = { viewModel.onMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, SignInMode.entries.size),
                        ) {
                            Text(
                                stringResource(
                                    if (mode == SignInMode.PASSWORD) R.string.signin_mode_password else R.string.signin_mode_token,
                                ),
                            )
                        }
                    }
                }

                when (state.mode) {
                    SignInMode.PASSWORD -> {
                        OutlinedTextField(
                            value = state.username,
                            onValueChange = viewModel::onUsername,
                            label = { Text(stringResource(R.string.signin_username)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = state.password,
                            onValueChange = viewModel::onPassword,
                            label = { Text(stringResource(R.string.signin_password)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    SignInMode.TOKEN -> {
                        OutlinedTextField(
                            value = state.token,
                            onValueChange = viewModel::onToken,
                            label = { Text(stringResource(R.string.signin_token)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }

                Button(
                    onClick = viewModel::submit,
                    enabled = state.canSubmit,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    if (state.submitting) {
                        CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.signin_button))
                    }
                }
            }
        }
    }
}
