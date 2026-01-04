package top.fifthlight.armorstand.ui.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import net.minecraft.client.gui.widget.SliderWidget
import net.minecraft.text.Text
import net.minecraft.util.math.MathHelper
import top.fifthlight.armorstand.ui.model.ViewModel
import top.fifthlight.armorstand.ui.screen.ArmorStandScreen
import kotlin.math.pow
import kotlin.math.roundToInt

abstract class RangedSliderWidget(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    text: Text,
    value: Double,
) : SliderWidget(x, y, width, height, text, value) {
    abstract val realValue: Double
    abstract val min: Double
    abstract val max: Double

    abstract fun updateValue(value: Double)
    abstract fun updateRange(min: Double, max: Double)
    abstract fun update(value: Double, min: Double, max: Double)
}

private class RangedSliderWidgetImpl(
    value: Double,
    min: Double,
    max: Double,
    width: Int,
    height: Int,
    private val messageFactory: (RangedSliderWidget, String) -> Text,
    private val decimalPlaces: Int? = 2,
    private val onValueChanged: (Boolean, Double) -> Unit,
) : RangedSliderWidget(
    x = 0,
    y = 0,
    width = width,
    height = height,
    text = Text.empty(),
    value = value,
) {
    override var realValue = value
        private set
    override var min = min
        private set
    override var max = max
        private set

    private fun updateProgressValue() {
        val progressValue = (realValue - min) / (max - min)
        this.value = MathHelper.clamp(progressValue, 0.0, 1.0)
        this.updateMessage()
    }

    override fun updateValue(value: Double) {
        this.realValue = value
        updateProgressValue()
    }

    override fun updateRange(min: Double, max: Double) {
        this.min = min
        this.max = max
        updateProgressValue()
    }

    override fun update(value: Double, min: Double, max: Double) {
        this.realValue = value
        this.min = min
        this.max = max
        updateProgressValue()
    }

    fun updateText() = updateMessage()

    fun setValue(value: Double) {
        val prevValue = this.value
        this.value = MathHelper.clamp(value, 0.0, 1.0)
        if (prevValue != this.value) {
            this.applyValue(false)
        }

        this.updateMessage()
    }

    private fun Double.toString(decimalPlaces: Int = 2) = "%.${decimalPlaces}f".format(this)
    override fun updateMessage() {
        this.message = messageFactory(this, if (decimalPlaces != null) {
            realValue.toString(decimalPlaces)
        } else {
            realValue.toString()
        })
    }

    private val scale = decimalPlaces?.let { 10.0.pow(it) }
    private fun Double.round() = if (scale != null) {
        (this * scale).roundToInt() / scale
    } else {
        this
    }
    override fun applyValue() = applyValue(true)
    fun applyValue(userTriggered: Boolean = false) {
        realValue = (this.value * (max - min) + min).round()
        onValueChanged(userTriggered, realValue)
    }
}

fun <T : ArmorStandScreen<T, M>, M : ViewModel> ArmorStandScreen<T, M>.slider(
    textFactory: (RangedSliderWidget, String) -> Text,
    width: Int = 150,
    height: Int = 20,
    min: Double = 0.0,
    max: Double = 1.0,
    decimalPlaces: Int? = 2,
    value: Flow<Double>,
    onValueChanged: (Boolean, Double) -> Unit,
): RangedSliderWidget = RangedSliderWidgetImpl(
    value = 0.0,
    min = min,
    max = max,
    width = width,
    height = height,
    messageFactory = textFactory,
    decimalPlaces = decimalPlaces,
    onValueChanged = onValueChanged,
).apply {
    updateText()
    scope.launch {
        value.collect {
            updateValue(it)
        }
    }
}
