package org.jeudego.pairgoth.util

import org.apache.commons.fileupload.FileItemIterator
import org.apache.commons.fileupload.FileItemStream
import org.apache.commons.fileupload.FileUploadException
import org.apache.commons.fileupload.servlet.ServletFileUpload
import org.apache.commons.fileupload.util.Streams
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import javax.servlet.http.HttpServletRequest

object Upload {
    internal var logger = LoggerFactory.getLogger("upload")
    const val SIZE_RANDOM = 20
    @Throws(IOException::class, FileUploadException::class)
    fun handleFileUpload(request: HttpServletRequest): List<Pair<String, ByteArray>> {
        // Check that we have a file upload request
        val isMultipart: Boolean = ServletFileUpload.isMultipartContent(request)
        if (!isMultipart) {
            logger.warn("multipart content expected")
            return listOf()
        }
        val files = mutableListOf<Pair<String, ByteArray>>()

        // Create a new file upload handler
        val upload = ServletFileUpload()
        val iter: FileItemIterator = upload.getItemIterator(request)

        // over all fields
        while (iter.hasNext()) {
            val item: FileItemStream = iter.next()
            val name: String = item.fieldName
            val stream: InputStream = item.openStream()
            if (item.isFormField) {
                // standard fields set into request attributes
                request.setAttribute(name, Streams.asString(stream))
            } else {
                val filename: String = item.name
                if (StringUtils.isEmpty(filename)) {
                    // ignoring empty file
                    continue
                }
                val input: InputStream = item.openStream()
                val bytes = ByteArrayOutputStream()
                Streams.copy(input, bytes, true)
                files.add(Pair(filename, bytes.toByteArray()))
            }
        }
        return files
    }
}