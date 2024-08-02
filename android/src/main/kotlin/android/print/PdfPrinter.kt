package android.print

import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File

class PdfPrint(private val printAttributes: PrintAttributes) {

    fun print(printAdapter: PrintDocumentAdapter, path: String, callback: CallbackPrint) {
        val parcelFileDescriptor = getOutputFile(path)
        if (parcelFileDescriptor == null) {
            callback.onFailure()
            return
        }

        printAdapter.onLayout(
            null, printAttributes, null, object : PrintDocumentAdapter.LayoutResultCallback() {
                override fun onLayoutFinished(info: PrintDocumentInfo, changed: Boolean) {
                    printAdapter.onWrite(arrayOf(PageRange.ALL_PAGES),
                        parcelFileDescriptor,
                        CancellationSignal(),
                        object : PrintDocumentAdapter.WriteResultCallback() {
                            override fun onWriteFinished(pages: Array<PageRange>) {
                                super.onWriteFinished(pages)
                                if (pages.isNotEmpty()) {
                                    callback.success(path)
                                } else {
                                    callback.onFailure()
                                }
                            }
                        })
                }
            }, null
        )
    }

    private fun getOutputFile(path: String): ParcelFileDescriptor? {
        val file = File(path)
        return try {
            file.createNewFile()
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open ParcelFileDescriptor", e)
            null
        }
    }

    interface CallbackPrint {
        fun success(path: String)
        fun onFailure()
    }

    companion object {
        private val TAG = PdfPrint::class.java.simpleName
    }
}
