package com.example.missiontrackermap.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.missiontrackermap.model.CalibrationPoint

@Composable
fun EditMissionPointDialog(
    point: CalibrationPoint,
    imageWidth: Int,
    imageHeight: Int,
    onSave: (name: String, objective: String?, pixelX: Double, pixelY: Double) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(point.name) }
    var objective by remember { mutableStateOf(point.missionObjective ?: "") }
    var pixelX by remember { mutableStateOf(point.pixel.x.toInt().toString()) }
    var pixelY by remember { mutableStateOf(point.pixel.y.toInt().toString()) }

    val parsedX = pixelX.toDoubleOrNull()
    val parsedY = pixelY.toDoubleOrNull()
    val isValid = name.isNotBlank()
            && parsedX != null && parsedX >= 0 && parsedX <= imageWidth
            && parsedY != null && parsedY >= 0 && parsedY <= imageHeight

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Point") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Point Name") },
                    singleLine = true,
                    isError = name.isBlank(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = objective,
                    onValueChange = { objective = it },
                    label = { Text("Mission Objective") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = pixelX,
                        onValueChange = { pixelX = it },
                        label = { Text("Pixel X") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = parsedX == null || parsedX < 0 || parsedX > imageWidth,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = pixelY,
                        onValueChange = { pixelY = it },
                        label = { Text("Pixel Y") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = parsedY == null || parsedY < 0 || parsedY > imageHeight,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    onSave(
                        name.trim(),
                        objective.trim().ifBlank { null },
                        parsedX!!,
                        parsedY!!
                    )
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
