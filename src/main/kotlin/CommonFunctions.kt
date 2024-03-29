import java.awt.Color
import java.awt.Dimension
import java.awt.Shape
import java.awt.font.TextLayout
import java.awt.geom.Line2D
import java.awt.geom.Rectangle2D
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

fun renderShapeWithOutline(shape: Shape, fillColor: Color, SVGCanvas: SVGCanvas) {
    SVGCanvas.color = fillColor
    SVGCanvas.fill(shape)
    SVGCanvas.color = strokeColor
    SVGCanvas.draw(shape)
}

/**
 * For segment [0, maxValue] returns a list containing labels for values axis.
 * Those labels should conform to certain rules, such as each label x (except zero) should be
 * in [floor(log10(x)), ceil(log10(x))] and y = x / 10 ^ floor(log(10)) should be in {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10}
 * or {0, 2, 4, 6, 8} or {0, 5}.
 */
fun getTenPowers(maxValue: Double, logDelta: Double): List<Double> {
    val result = mutableListOf<Double>()

    val step = (10.0).pow(floor(log10(maxValue)) + logDelta)
    for (i in 0..ceil(maxValue / step).toInt()) {
        result.add(i.toDouble() * step)
    }

    return result
}

/**
 * Return delta between two consecutive numbers from the output of [getLinearInterpolation] function.
 */
fun getLinearInterpolationDelta(start: Double, end: Double, steps: Int): Double = (end - start) / (steps - 1)

/**
 * Return [steps] values that match this pattern: start, start + delta, start + 2 * delta, ..., finish
 */
fun getLinearInterpolation(start: Double, end: Double, steps: Int): List<Double> {
    val delta = getLinearInterpolationDelta(start, end, steps)
    val res = mutableListOf<Double>()

    for (i in 0 until steps) {
        res.add(start + i.toDouble() * delta)
    }

    return res
}

/**
 * Return delta between two consecutive numbers from the output of [getLinearInterpolationMiddles] function.
 */
fun getLinearInterpolationMiddlesDelta(start: Double, end: Double, steps: Int): Double = (end - start) / (steps)

/**
 * Return [steps] values that match this pattern: start + 0.5 * delta, start + 1.5 * delta, ..., finish - 0.5 * delta
 */
fun getLinearInterpolationMiddles(start: Double, end: Double, steps: Int): List<Double> {
    val delta = getLinearInterpolationMiddlesDelta(start, end, steps)
    val res = mutableListOf<Double>()

    for (i in 0 until steps) {
        res.add(start + (i.toDouble() + 0.5) * delta)
    }

    return res
}

/**
 * Returns [title] rectangle.
 */
fun getTitleRectangle(title: String, width: Int, SVGCanvas: SVGCanvas): Rectangle2D.Double {
    val titleRectangle = Rectangle2D.Double()
    titleRectangle.apply {
        x = defaultMargin
        y = defaultMargin
        this.width = width.toDouble() - 2 * defaultMargin
        height = 0.0
    }

    if (title.isNotEmpty()) {
        val titleLayout = TextLayout(title, titleFont, SVGCanvas.fontRenderContext)
        titleRectangle.height = 2 * defaultMargin + titleLayout.bounds.height
    }
    return titleRectangle
}

/**
 * Renders provided [title] in provided [titleRectangle].
 */
fun renderTitle(title: String, titleRectangle: Rectangle2D.Double, SVGCanvas: SVGCanvas) {
    if (title.isNotEmpty()) {
        val titleLayout = TextLayout(title, titleFont, SVGCanvas.fontRenderContext)

        titleLayout.draw(
            SVGCanvas, (titleRectangle.centerX - (titleLayout.bounds.width / 2.0) - titleLayout.bounds.x).toFloat(),
            (titleRectangle.centerY - (titleLayout.bounds.height / 2.0) - titleLayout.bounds.y).toFloat()
        )
    }
}

/**
 * Renders grid (those light gray lines that help you estimate the value that bar represents)
 */
fun renderGrid(gridRectangle: Rectangle2D, numberOfLines: Int, orientation: Orientation, SVGCanvas: SVGCanvas) {
    when (orientation) {
        Orientation.VERTICAL -> {
            getLinearInterpolation(gridRectangle.minY, gridRectangle.maxY, numberOfLines).forEach { y ->
                SVGCanvas.color = gridColor
                SVGCanvas.draw(Line2D.Double(gridRectangle.minX, y, gridRectangle.maxX, y))
            }
        }
        Orientation.HORIZONTAL -> {
            getLinearInterpolation(gridRectangle.minX, gridRectangle.maxX, numberOfLines).forEach { x ->
                SVGCanvas.color = gridColor
                SVGCanvas.draw(Line2D.Double(x, gridRectangle.minY, x, gridRectangle.maxY))
            }
        }
    }
}

fun getGraphRectangle(titleRectangle: Rectangle2D.Double, size: Dimension, legendRectangle: Rectangle2D.Double): Rectangle2D.Double =
    Rectangle2D.Double().apply {
        x = defaultMargin
        y = titleRectangle.maxY
        width = size.width.toDouble() - 2 * defaultMargin
        height = legendRectangle.minY - titleRectangle.maxY
    }

fun getGridRectangle(graphRectangle: Rectangle2D.Double, xAxisLabelsLayouts: List<TextLayout>, yAxisLabelsLayouts: List<TextLayout>): Rectangle2D.Double =
    Rectangle2D.Double().apply {
        x = graphRectangle.minX + defaultMargin + yAxisLabelsLayouts.maxOf { it.bounds.width }
        y = graphRectangle.minY + defaultMargin
        width = graphRectangle.width - 2 * defaultMargin - yAxisLabelsLayouts.maxOf { it.bounds.width }
        height = graphRectangle.height - 2 * defaultMargin - xAxisLabelsLayouts.maxOf { it.bounds.height }
    }

/**
 * Returns legend rectangle.
 */
fun setLegendRectangle(size: Dimension, columnsLabelsLayouts: List<TextLayout>, displayLegend: Boolean = true): Rectangle2D.Double =
    Rectangle2D.Double().apply {
        x = defaultMargin
        y = size.height.toDouble() - defaultMargin - columnsLabelsLayouts.maxOf { it.bounds.height }
        width = size.width.toDouble() - 2 * defaultMargin
        height =
            if (displayLegend) columnsLabelsLayouts.maxOf { it.bounds.height } else 0.0 + 2 * defaultMargin
    }

/**
 * Renders legend if [displayLegend] is true.
 */
fun renderLegend(
    displayLegend: Boolean,
    columnsLabelsLayouts: List<TextLayout>,
    columnsColors: List<Color>,
    legendRectangle: Rectangle2D,
    SVGCanvas: SVGCanvas
) {
    if (!displayLegend) {
        return
    }

    val n = columnsLabelsLayouts.size.toDouble()
    val legendHeight = columnsLabelsLayouts.maxOf { it.bounds.height }
    val legendWidth =
        n * legendHeight + columnsLabelsLayouts.sumOf { it.bounds.width } + (2 * (n - 1) + n) * defaultMargin
    var currentX = legendRectangle.centerX - (legendWidth / 2.0)
    columnsLabelsLayouts.zip(columnsColors).forEach { (layout, color) ->
        val colorSquare = Rectangle2D.Double(currentX, legendRectangle.minY + defaultMargin, legendHeight, legendHeight)

        SVGCanvas.color = color
        SVGCanvas.fill(colorSquare)
        SVGCanvas.color = strokeColor
        SVGCanvas.draw(colorSquare)

        currentX += legendHeight + defaultMargin

        layout.draw(
            SVGCanvas,
            (currentX - layout.bounds.x).toFloat(),
            (legendRectangle.minY + defaultMargin - layout.bounds.y).toFloat()
        )

        currentX += layout.bounds.width + 2 * defaultMargin
    }
}