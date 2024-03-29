import java.awt.Color
import java.awt.font.TextLayout
import java.awt.geom.Arc2D
import java.awt.geom.Rectangle2D

/**
 * The data for rendering chart itself. [values] stores the 2D table with data (as in Excel). Only the first row is
 * taken from [values]; others are ignored.
 */
data class PieChartData(
    val chartTitle: String,
    val columnsTitles: List<String>,
    val values: List<List<Double>>
)

/**
 * Represents all data needed for rendering pie chart.
 */
data class PieChart(val data: PieChartData, val style: PieChartStyle, val SVGCanvas: SVGCanvas) {
    /**
     * [titleRectangle], [graphRectangle], [pieRectangle] and [legendRectangle] represent rectangles in which corresponding
     * parts of the chart are rendered (with [defaultMargin] indentation)
     */
    val titleRectangle = getTitleRectangle(data.chartTitle, style.size.width, SVGCanvas)
    val graphRectangle = Rectangle2D.Double()
    val pieRectangle = Rectangle2D.Double()
    var legendRectangle = Rectangle2D.Double()

    /**
     * Stores the color of each column.
     */
    val columnsColors = mutableListOf<Color>()

    /**
     * Sum of all data values (needed to calculate angles of sectors)
     */
    var dataSum = 0.0

    /**
     * [columnsLabelsLayouts] stores TextLayout objects for virtually all text on the chart. We need to store
     * TextLayout objects to properly calculate different sizes & indentations.
     */
    val columnsLabelsLayouts = mutableListOf<TextLayout>()

    /**
     * Internal variable used for assigning colors to columns.
     */
    var colorIndex = -1

    /**
     * Internal function, which returns the pointer of the next color. Used for assigning colors to columns.
     */
    fun nextColor(): Int {
        colorIndex = (colorIndex + 1) % style.sectorsColors.size
        return colorIndex
    }

    /**
     * Sets the [dataSum] value.
     */
    fun setDataSum() {
        dataSum = data.values[0].sum()
    }

    /**
     * Generates all text layouts except chart title.
     */
    fun generateAllLabelsLayouts() {
        data.columnsTitles.forEach {
            columnsLabelsLayouts.add(TextLayout(it, labelFont, SVGCanvas.fontRenderContext))
        }
    }

    /**
     * Calculates graph and grid rectangles. Graph rectangle includes grid, and both axes labels.
     */
    fun setGraphAndPieRectangle() {
        graphRectangle.apply {
            x = defaultMargin
            y = titleRectangle.maxY
            width = style.size.width.toDouble() - 2 * defaultMargin
            height = legendRectangle.minY - titleRectangle.maxY
        }

        pieRectangle.apply {
            y = graphRectangle.y + defaultMargin
            height = graphRectangle.height - 2 * defaultMargin
            width = height
            x = graphRectangle.centerX - width / 2.0
        }
    }

    /**
     * Assigns color to sectors corresponding to different columns.
     */
    fun setSectorsColors() {
        repeat(data.values[0].size) {
            columnsColors.add(style.sectorsColors[nextColor()])
        }
    }

    /**
     * Renders the pie.
     */
    fun renderPie() {
        val firstRow = data.values[0]
        var currentAngle = 90.0
        firstRow.zip(columnsColors).reversed().forEach { (cell, color) ->
            val cellAngle = (cell / dataSum) * (360.0)
            val arc = Arc2D.Double(
                pieRectangle.x,
                pieRectangle.y,
                pieRectangle.width,
                pieRectangle.height,
                currentAngle,
                cellAngle,
                Arc2D.PIE
            )

            renderShapeWithOutline(arc, color, SVGCanvas)

            currentAngle += cellAngle
        }
    }

    /**
     * Renders whole chart.
     */
    fun render() {
        renderTitle(data.chartTitle, titleRectangle, SVGCanvas)
        setDataSum()

        generateAllLabelsLayouts()
        legendRectangle = setLegendRectangle(style.size, columnsLabelsLayouts, style.displayLegend)
        setGraphAndPieRectangle()

        setSectorsColors()
        renderPie()

        renderLegend(style.displayLegend, columnsLabelsLayouts, columnsColors, legendRectangle, SVGCanvas)
    }
}