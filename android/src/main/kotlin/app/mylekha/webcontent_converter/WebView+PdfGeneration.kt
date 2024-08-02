package app.mylekha.webcontent_converter

import android.print.HeaderPrintDocumentAdapter
import android.print.PdfPrint
import android.print.PrintAttributes
import android.webkit.WebView
import java.io.File

/**
 * Extension function for WebView to export content as PDF.
 *
 * @param savedPath The path where the PDF file will be saved.
 * @param format A map containing the width and height of the PDF in inches.
 * @param footerText Optional text to display in the footer of the PDF as page counter. E.g. Page.
 * @param headerText Optional text to display in the header of the PDF.
 * @param callback Callback to be invoked when the PDF export is complete.
 */
fun WebView.exportAsPdfFromWebView(
    savedPath: String,
    format: Map<String, Double>,
    footerText: String?,
    headerText: String?,
    callback: PdfPrint.CallbackPrint
) {
    // Convert width and height from inches to pixels
    val width = format["width"]
    val height = format["height"]

    if(width == null || height == null) {
        return
    }

    // Create print attributes for the PDF
    val attributes = PrintAttributes.Builder()
        .setMediaSize(
            PrintAttributes.MediaSize(
                "$width-$height",
                "android",
                width.convertFromInchesToInt(),
                height.convertFromInchesToInt()
            )
        )
        .setResolution(PrintAttributes.Resolution("pdf", "pdf", 600, 600))
        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
        .build()

    val file = File(savedPath)
    val pdfPrinter = PdfPrint(attributes)
    val adapter = HeaderPrintDocumentAdapter(
        this.createPrintDocumentAdapter(file.name),
        headerText,
        footerText
    )

    pdfPrinter.print(adapter, file.absolutePath, callback)
}

/**
 * Extension function to convert inches to milli inches.
 */
fun Double.convertFromInchesToInt(): Int {
    return (this * 1000).toInt()
}

