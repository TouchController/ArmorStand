package top.fifthlight.armorstand.ui.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.text.Text
import top.fifthlight.armorstand.ui.model.ViewModel
import top.fifthlight.armorstand.ui.screen.ArmorStandScreen

fun <T : ArmorStandScreen<T, M>, M : ViewModel> ArmorStandScreen<T, M>.textField(
    placeHolder: Text? = null,
    width: Int = this.width,
    height: Int = 20,
    text: Flow<String>,
    maxChars: Int? = null,
    onChanged: (String) -> Unit,
) = TextFieldWidget(textRenderer, width, height, Text.empty()).apply {
    placeHolder?.let {
        setPlaceholder(placeHolder)
    }
    setMaxLength(maxChars ?: Int.MAX_VALUE)
    setChangedListener {
        onChanged(it)
    }
    scope.launch {
        text.collect { newText ->
            if (newText != this@apply.text) {
                this@apply.text = newText
            }
        }
    }
}