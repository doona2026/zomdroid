package com.zomdroid.ui.controls

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import com.zomdroid.R
import com.zomdroid.input.AbstractControlElement
import com.zomdroid.input.ButtonControlElement
import com.zomdroid.input.ControlElementDescription
import com.zomdroid.input.DpadControlElement
import com.zomdroid.input.GLFWBinding
import com.zomdroid.input.InputControlsView
import com.zomdroid.input.MouseStickControlElement
import com.zomdroid.input.RadialMenuControlElement
import com.zomdroid.input.ScrollBarControlElement
import com.zomdroid.input.StickControlElement
import com.zomdroid.input.TouchpadControlElement
import com.zomdroid.ui.component.ZomdroidGlassCard
import com.zomdroid.ui.component.ZomdroidGlassSurface

interface ControlsEditorHost {
    fun onPickCustomIcon()
    fun onExit()
}

fun installControlsEditorScreen(view: ComposeView, inputView: InputControlsView, host: ControlsEditorHost) {
    view.setContent { ControlsEditorScreen(inputView, host) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ControlsEditorScreen(inputView: InputControlsView, host: ControlsEditorHost) {
    var selected by remember { mutableStateOf<AbstractControlElement?>(inputView.selectedElement) }
    var showAdd by remember { mutableStateOf(false) }
    var showLayouts by remember { mutableStateOf(false) }
    var showInstructions by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }

    DisposableEffect(inputView) {
        val listener = object : InputControlsView.EditorListener {
            override fun onElementSelected(element: AbstractControlElement) { selected = element; revision++ }
            override fun onElementDeselected() { selected = null; revision++ }
            override fun onAddElementRequested() { showAdd = true }
            override fun onElementDeleted() { selected = null; revision++ }
        }
        inputView.setEditorListener(listener)
        onDispose { inputView.setEditorListener(null) }
    }
    LaunchedEffect(Unit) { showInstructions = true }
    @Suppress("UNUSED_VARIABLE") val ignoredRevision = revision

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { inputView }, modifier = Modifier.fillMaxSize())
        TopAppBar(
            title = { Text(stringResource(R.string.fragment_label_controls_editor)) },
            navigationIcon = { IconButton(onClick = host::onExit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.app_shell_back)) } },
            actions = {
                IconButton(onClick = { showLayouts = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.controls_editor_layout_dialog_title)) }
            },
        )
        selected?.let { element ->
            EditorPanel(
                element = element,
                inputView = inputView,
                onChanged = { inputView.invalidate(); revision++ },
                onPickIcon = host::onPickCustomIcon,
                onDelete = { inputView.deleteSelectedElement() },
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(top = 64.dp).widthIn(max = 420.dp),
            )
        }
    }

    if (showAdd) AddElementDialog(inputView, onDismiss = { showAdd = false })
    if (showLayouts) LayoutDialog(inputView, onDismiss = { showLayouts = false })
    if (showInstructions) {
        AlertDialog(
            onDismissRequest = { showInstructions = false },
            title = { Text(stringResource(R.string.dialog_title_info)) },
            text = { Text(stringResource(R.string.controls_editor_instructions)) },
            confirmButton = { TextButton(onClick = { showInstructions = false }) { Text(stringResource(R.string.dialog_button_ok)) } },
        )
    }
}

@Composable
private fun EditorPanel(
    element: AbstractControlElement,
    inputView: InputControlsView,
    onChanged: () -> Unit,
    onPickIcon: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier,
) {
    val scroll = rememberScrollState()
    ZomdroidGlassSurface(modifier.padding(12.dp)) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(element.getType().name, style = MaterialTheme.typography.titleMedium)
            SliderSetting(stringResource(R.string.control_element_scale), element.getScale(), 0.25f..2f) { element.setScale(it); onChanged() }
            SliderSetting(stringResource(R.string.control_element_opacity), element.getAlpha() / 255f, 0f..1f) { element.setAlpha((it * 255).toInt()); onChanged() }
            OutlinedButton(onClick = { val alpha = element.getAlpha(); inputView.getControlElements().forEach { it.setAlpha(alpha) }; onChanged() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.control_element_opacity_all))
            }

            val sensitivity = sensitivityOf(element)
            if (sensitivity != null) SliderSetting(stringResource(R.string.control_element_sensitivity), sensitivity, 0f..2f) { setSensitivity(element, it); onChanged() }
            if (element is TouchpadControlElement) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.control_element_tap_click), Modifier.weight(1f))
                    Switch(checked = !element.isTapDisabled(), onCheckedChange = { element.setTapDisabled(!it); onChanged() })
                }
            }

            val fixedMnk = element.getType() == AbstractControlElement.Type.STICK_WASD || element.getType() == AbstractControlElement.Type.STICK_MOUSE
            if (!fixedMnk) {
                DropdownSetting(stringResource(R.string.control_element_input_type), element.getInputType().name, AbstractControlElement.InputType.values().map { it.name }) {
                    element.setInputType(AbstractControlElement.InputType.valueOf(it)); onChanged()
                }
            }
            when (element.getType()) {
                AbstractControlElement.Type.BUTTON_CIRCLE, AbstractControlElement.Type.BUTTON_RECT -> ButtonSettings(element, inputView, onChanged, onPickIcon)
                AbstractControlElement.Type.DPAD -> DirectionSettings(element, onChanged)
                AbstractControlElement.Type.STICK -> StickSettings(element, onChanged)
                AbstractControlElement.Type.RADIAL_MENU -> DirectionSettings(element, onChanged)
                else -> Unit
            }
            OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Delete, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.control_element_delete))
            }
        }
    }
}

@Composable
private fun ButtonSettings(element: AbstractControlElement, inputView: InputControlsView, onChanged: () -> Unit, onPickIcon: () -> Unit) {
    OutlinedTextField(value = element.getText(), onValueChange = { element.setText(it); onChanged() }, label = { Text(stringResource(R.string.control_element_text)) }, modifier = Modifier.fillMaxWidth())
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.control_element_bindings), Modifier.weight(1f))
        Checkbox(checked = element.getToggle(), onCheckedChange = { element.setToggle(it); onChanged() })
    }
    if (hasStartSelectBinding(element)) DropdownSetting(stringResource(R.string.control_element_icon), element.getIcon()?.name ?: "", ControlElementDescription.Icon.values().map { it.name }) {
        element.setIcon(ControlElementDescription.Icon.valueOf(it)); onChanged()
    }
    if (element is ButtonControlElement) DropdownSetting(stringResource(R.string.control_element_style), element.getStyle().name, ControlElementDescription.Style.values().map { it.name }) {
        element.setStyle(ControlElementDescription.Style.valueOf(it)); onChanged()
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onPickIcon, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.control_element_custom_icon)) }
        Checkbox(checked = (element as? ButtonControlElement)?.isNoTint() ?: false, onCheckedChange = { (element as? ButtonControlElement)?.setNoTint(it); onChanged() })
    }
    BindingList(element, onChanged)
}

@Composable
private fun BindingList(element: AbstractControlElement, onChanged: () -> Unit) {
    element.getBindings().forEachIndexed { index, binding ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            DropdownSetting(stringResource(R.string.control_element_select_binding), binding.name, GLFWBinding.valuesForType(element.getInputType()).map { it.name }) {
                element.setBinding(index, GLFWBinding.valueOf(it)); onChanged()
            }
            IconButton(onClick = { element.removeBinding(index); onChanged() }) { Icon(Icons.Default.Delete, stringResource(R.string.control_element_delete)) }
        }
    }
    OutlinedButton(onClick = { element.addBinding(GLFWBinding.valuesForType(element.getInputType()).first()); onChanged() }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.controls_editor_add_binding))
    }
}

@Composable
private fun DirectionSettings(element: AbstractControlElement, onChanged: () -> Unit) {
    val options = GLFWBinding.valuesForType(element.getInputType()).map { it.name }
    DirectionDropdown(stringResource(R.string.control_element_binding_left), element.getBindingLeft(), options) { element.setBindingLeft(GLFWBinding.valueOf(it)); radialLabel(element, 0, it); onChanged() }
    DirectionDropdown(stringResource(R.string.control_element_binding_up), element.getBindingUp(), options) { element.setBindingUp(GLFWBinding.valueOf(it)); radialLabel(element, 1, it); onChanged() }
    DirectionDropdown(stringResource(R.string.control_element_binding_right), element.getBindingRight(), options) { element.setBindingRight(GLFWBinding.valueOf(it)); radialLabel(element, 2, it); onChanged() }
    DirectionDropdown(stringResource(R.string.control_element_binding_down), element.getBindingDown(), options) { element.setBindingDown(GLFWBinding.valueOf(it)); radialLabel(element, 3, it); onChanged() }
}

@Composable
private fun StickSettings(element: AbstractControlElement, onChanged: () -> Unit) {
    DirectionSettings(element, onChanged)
    if (element.getInputType() == AbstractControlElement.InputType.GAMEPAD) {
        DropdownSetting(stringResource(R.string.control_element_stick_binding), element.getBindingStick().name, listOf(GLFWBinding.LEFT_JOYSTICK.name, GLFWBinding.RIGHT_JOYSTICK.name)) {
            element.setBindingStick(GLFWBinding.valueOf(it)); onChanged()
        }
    }
}

@Composable
private fun DirectionDropdown(label: String, current: GLFWBinding?, options: List<String>, onSelected: (String) -> Unit) {
    DropdownSetting(label, current?.name ?: options.firstOrNull().orEmpty(), options, onSelected)
}

@Composable
private fun SliderSetting(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column { Row { Text(label); Spacer(Modifier.weight(1f)); Text(stringResource(R.string.percentage_format, (value * 100).toInt())) }; Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onValueChange, valueRange = range) }
}

@Composable
private fun DropdownSetting(label: String, value: String, options: List<String>, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(value.ifBlank { stringResource(R.string.control_element_text_empty) }) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { expanded = false; onSelected(option) }) }
            }
        }
    }
}

@Composable
private fun AddElementDialog(inputView: InputControlsView, onDismiss: () -> Unit) {
    val types = AbstractControlElement.Type.values().filterNot { it.name.startsWith("DPAD_") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.controls_editor_add_element)) }, text = {
        Column { types.forEach { type -> TextButton(onClick = { inputView.addControlElementForEditor(type); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text(type.name) } } }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_button_cancel)) } })
}

@Composable
private fun LayoutDialog(inputView: InputControlsView, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.controls_editor_layout_dialog_title)) }, text = {
        Column {
            TextButton(onClick = { inputView.replaceControlsFromAsset("default_controls_vkbd.json", true); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.controls_editor_load_vkbd)) }
            TextButton(onClick = { inputView.replaceControlsFromAsset(com.zomdroid.C.assets.DEFAULT_CONTROLS, true); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.controls_editor_reset_gamepad)) }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_button_cancel)) } })
}

private fun sensitivityOf(element: AbstractControlElement): Float? = when (element) {
    is TouchpadControlElement -> element.getSensitivity()
    is MouseStickControlElement -> element.getSensitivity()
    is ScrollBarControlElement -> element.getSensitivity()
    is RadialMenuControlElement -> element.getSensitivity()
    else -> null
}

private fun setSensitivity(element: AbstractControlElement, value: Float) {
    when (element) {
        is TouchpadControlElement -> element.setSensitivity(value)
        is MouseStickControlElement -> element.setSensitivity(value)
        is ScrollBarControlElement -> element.setSensitivity(value)
        is RadialMenuControlElement -> element.setSensitivity(value)
    }
}

private fun radialLabel(element: AbstractControlElement, index: Int, name: String) {
    if (element is RadialMenuControlElement) element.setSectorLabel(index, name.substringAfterLast('_'))
}

private fun hasStartSelectBinding(element: AbstractControlElement): Boolean = element.getBindings().any {
    it == GLFWBinding.GAMEPAD_BUTTON_BACK || it == GLFWBinding.GAMEPAD_BUTTON_START || it == GLFWBinding.GAMEPAD_BUTTON_GUIDE
}
