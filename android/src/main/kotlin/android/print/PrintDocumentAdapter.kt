package android.print

import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import com.itextpdf.kernel.colors.Color
import com.itextpdf.kernel.colors.DeviceGray
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.VerticalAlignment
import java.io.File
import java.io.FileOutputStream

class HeaderPrintDocumentAdapter(
    private val wrappedAdapter: PrintDocumentAdapter,
    private val headerText: String?,
    private val footerText: String?
) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?
    ) {
        wrappedAdapter.onLayout(oldAttributes, newAttributes, cancellationSignal, callback, extras)
    }

    override fun onWrite(
        pageRanges: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        val tempFile = createTempFile()
        val tempFd = openTempFile(tempFile)

        wrappedAdapter.onWrite(pageRanges, tempFd, cancellationSignal, object : WriteResultCallback() {
            override fun onWriteFinished(pages: Array<out PageRange>?) {
                super.onWriteFinished(pages)
                try {
                    processPdf(tempFile, destination, pages, callback)
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.message)
                } finally {
                    closeResources(tempFd, tempFile)
                }
            }

            override fun onWriteFailed(error: CharSequence?) {
                super.onWriteFailed(error)
                callback?.onWriteFailed(error)
            }
        })
    }

    override fun onFinish() {
        wrappedAdapter.onFinish()
    }

    private fun createTempFile(): File {
        return File.createTempFile("temp", ".pdf")
    }

    private fun openTempFile(tempFile: File): ParcelFileDescriptor {
        return ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_WRITE)
    }

    private fun processPdf(tempFile: File, destination: ParcelFileDescriptor?, pages: Array<out PageRange>?, callback: WriteResultCallback?) {
        val reader = PdfReader(tempFile)
        val writer = PdfWriter(FileOutputStream(destination?.fileDescriptor))
        val pdfDoc = PdfDocument(reader, writer)
        val document = Document(pdfDoc)

        val grayColor = DeviceGray(0.5f)

        for (i in 1..pdfDoc.numberOfPages) {
            val pageSize = pdfDoc.getPage(i).pageSize
            if (headerText != null) {
                addHeader(document, headerText, pageSize, i, grayColor)
            }

            if(footerText != null) {
                addFooter(document, footerText, pageSize, i, grayColor)
            }

        }

        document.close()
        pdfDoc.close()
        reader.close()

        callback?.onWriteFinished(pages)
    }

    private fun addHeader(document: Document, headerText: String, pageSize: Rectangle, pageNumber: Int, textColor: Color) {
        val headerHeight = pageSize.top - 20f
        val headerParagraph = Paragraph(headerText)
            .setTextAlignment(TextAlignment.CENTER)
            .setFontColor(textColor)

        document.showTextAligned(
            headerParagraph,
            pageSize.width / 2,
            headerHeight,
            pageNumber,
            TextAlignment.CENTER,
            VerticalAlignment.TOP,
            0f
        )
    }
    private fun addFooter(document: Document, footerText: String, pageSize: Rectangle, pageNumber: Int, textColor: Color) {

        val footerHeight = 20f
        val footerParagraph = Paragraph("$footerText $pageNumber")
            .setTextAlignment(TextAlignment.CENTER)
            .setFontColor(textColor)

        document.showTextAligned(
            footerParagraph,
            pageSize.width / 2,
            footerHeight,
            pageNumber,
            TextAlignment.CENTER,
            VerticalAlignment.BOTTOM,
            0f
        )
    }

    private fun closeResources(tempFd: ParcelFileDescriptor, tempFile: File) {
        tempFd.close()
        tempFile.delete()
    }
}
