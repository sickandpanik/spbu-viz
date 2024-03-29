import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import org.apache.batik.swing.JSVGCanvas
import org.apache.batik.swing.gvt.GVTTreeRendererAdapter
import org.apache.batik.swing.gvt.GVTTreeRendererEvent
import org.apache.batik.swing.svg.GVTTreeBuilderAdapter
import org.apache.batik.swing.svg.GVTTreeBuilderEvent
import org.apache.batik.swing.svg.SVGDocumentLoaderAdapter
import org.apache.batik.swing.svg.SVGDocumentLoaderEvent
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.system.exitProcess

/**
 * Represents the whole CLI interface with all options available. Clikt magic.
 */
class Viz : CliktCommand() {
    val outputFile: File by option("-o", "--output", help = "The name of the output file").file(
        canBeDir = false,
        canBeSymlink = false,
    ).default(File("output.svg"))
    val inputFile: File by option(
        "-d",
        "--data",
        help = "(required) The name of the data file (in CSV format)"
    ).file(canBeDir = false, canBeSymlink = false, mustExist = true, mustBeReadable = true).required()
    val chartSize: Pair<Int, Int> by option(
        "-s",
        "--size",
        help = "Dimensions of the output file: first width, then height"
    ).int().pair().default(Pair(800, 600))
    val renderPNG: Boolean by option("-p", "--PNG", help = "Render PNG, if this option is present").flag()
    val extractRowsLabels: Boolean by option(
        "-r",
        "--rows-labels",
        help = "Treat first column as labels for rows in CSV"
    ).flag()
    val extractColumnLabels: Boolean by option(
        "-c",
        "--columns-labels",
        help = "Treat first row as labels for columns in CSV"
    ).flag()
    val chartType: String by option("-t", "--type", help = "(required) The type of the chart").choice(
        "bar",
        "histogram",
        "pie",
        "scatter"
    ).required()
    val isHorizontal: Boolean by option("--horizontal", help = "If bar is the selected chart type, make bars horizontal").flag()
    val isStacked: Boolean by option("--stacked", help = "If bar is the selected chart type, make different inputs of data stack on top of each other").flag()
    val barsCount: Int by option("-b", "--bars", help = "If histogram is the selected chart type, this option sets the number of bars (10 is default)").int().default(10)
    val dontShowWindow: Boolean by option("-m", "--minimize", help = "Don't show window with SVG").flag()
    val chartTitle: String? by option("--title", help = "Set the chart title")

    override fun run() {
        val parsedCSV = parseCSV(inputFile, extractRowsLabels, extractColumnLabels)

        if (parsedCSV == null) {
            println("Malformed input file.")
            return
        }

        val size = Dimension(chartSize.first, chartSize.second)
        val SVGChart = getEmptyChart(size)

        when (chartType) {
            "bar" -> {
                BarChart(
                    BarChartData(
                        chartTitle ?: "",
                        parsedCSV.columnsLabels,
                        parsedCSV.rowsLabels,
                        parsedCSV.values
                    ),
                    BarChartStyle(size = size,
                        orientation = if (isHorizontal) Orientation.HORIZONTAL else Orientation.VERTICAL,
                        multipleValuesDisplay = if (isStacked) BarChartMultipleValuesDisplay.STACKED else BarChartMultipleValuesDisplay.CLUSTERED
                    ),
                    SVGChart.SVGCanvas
                ).render()
            }
            "histogram" -> {
                HistogramChart(
                    HistogramChartData(
                        chartTitle ?: "",
                        parsedCSV.values,
                        if (barsCount <= 0) 10 else barsCount
                    ),
                    HistogramChartStyle(size = size),
                    SVGChart.SVGCanvas
                ).render()
            }
            "pie" -> {
                PieChart(
                    PieChartData(
                        chartTitle ?: "",
                        parsedCSV.columnsLabels,
                        parsedCSV.values
                    ),
                    PieChartStyle(size = size),
                    SVGChart.SVGCanvas
                ).render()
            }
            "scatter" -> {
                if (parsedCSV.values.any { it.size < 2 }) {
                    println("Malformed input file.")
                    return
                }

                ScatterChart(
                    ScatterChartData(
                        chartTitle ?: "",
                        parsedCSV.values
                    ),
                    ScatterChartStyle(size = size),
                    SVGChart.SVGCanvas
                ).render()
            }
        }

        val outCommonName = "${outputFile.parent ?: ""}${if (outputFile.parent != null) "/" else ""}${outputFile.nameWithoutExtension}"
        val outSVGName = "$outCommonName.svg"
        val outPNGName = "$outCommonName.png"

        SVGChart.SVGCanvas.stream(outSVGName)

        if (renderPNG) {
            rasterize(outSVGName, outPNGName, size)
        }

        if (!dontShowWindow) {
            createWindow("pf-2021-viz", outSVGName, size)
        }
    }
}

/**
 * Magic function that creates the window.
 */
fun createWindow(title: String, filename: String, size: Dimension) = runBlocking(Dispatchers.Swing) {
    val f = JFrame(title)
    val app = SVGApplication(f)

    f.contentPane.add(app.createComponents(filename))

    f.addWindowListener(object : WindowAdapter() {
        override fun windowClosing(e: WindowEvent) {
            exitProcess(0)
        }
    })
    f.setSize(size.width, size.height)
    f.isVisible = true
}

/**
 * Class that represents the window with rendered SVG.
 */
class SVGApplication(val frame: JFrame) {
    /**
     * Debug label which is displayed on top of the window to track the state of SVG rendering.
     */
    val label = JLabel()

    /**
     * Widget that renders SVG. Provided by the Apache Batik library
     */
    val svgCanvas = JSVGCanvas()

    /**
     * Magic functions that places widgets on window. Copied from Apache Batik documentation and slightly modified.
     */
    fun createComponents(filename: String): JComponent {
        val panel = JPanel(BorderLayout())
        val p = JPanel(FlowLayout(FlowLayout.LEFT))
        p.add(label)
        panel.add("North", p)
        panel.add("Center", svgCanvas)

        svgCanvas.uri = File(filename).toURI().toString()
        svgCanvas.setDocumentState(JSVGCanvas.ALWAYS_DYNAMIC)
        // Set the JSVGCanvas listeners.
        svgCanvas.addSVGDocumentLoaderListener(object : SVGDocumentLoaderAdapter() {
            override fun documentLoadingStarted(e: SVGDocumentLoaderEvent) {
                label.text = "Document Loading..."
            }

            override fun documentLoadingCompleted(e: SVGDocumentLoaderEvent) {
                label.text = "Document Loaded."
            }
        })
        svgCanvas.addGVTTreeBuilderListener(object : GVTTreeBuilderAdapter() {
            override fun gvtBuildStarted(e: GVTTreeBuilderEvent) {
                label.text = "Build Started..."
            }

            override fun gvtBuildCompleted(e: GVTTreeBuilderEvent) {
                label.text = "Build Done."
            }
        })
        svgCanvas.addGVTTreeRendererListener(object : GVTTreeRendererAdapter() {
            override fun gvtRenderingPrepare(e: GVTTreeRendererEvent) {
                label.text = "Rendering Started..."
            }

            override fun gvtRenderingCompleted(e: GVTTreeRendererEvent) {
                label.text = ""
            }
        })

        return panel
    }
}