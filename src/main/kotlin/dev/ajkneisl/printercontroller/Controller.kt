package dev.ajkneisl.printercontroller

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import com.github.anastaciocintra.escpos.EscPos
import com.github.anastaciocintra.escpos.EscPosConst
import com.github.anastaciocintra.escpos.Style
import com.github.anastaciocintra.escpos.barcode.QRCode
import com.github.anastaciocintra.escpos.image.*
import com.github.anastaciocintra.output.PrinterOutputStream
import dev.ajkneisl.printerlib.*
import java.io.IOException
import java.net.URL
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*
import javax.imageio.ImageIO
import javax.print.PrintService
import kotlin.system.exitProcess
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.litote.kmongo.KMongo
import org.litote.kmongo.eq
import org.litote.kmongo.getCollection
import org.slf4j.LoggerFactory

object Controller {
    private val LOGGER: org.slf4j.Logger = LoggerFactory.getLogger(this.javaClass)

    /** The printer. */
    private lateinit var PRINT_SERVICE: PrintService

    private val CENTER_JUSTIFY: Style = Style().setJustification(EscPosConst.Justification.Center)

    /** Print a [request]. */
    fun print(request: Print) {
        print(request.lines, request.createdAt, request.printId)
    }

    /** Print [lines], [createdAt], and [id]. */
    private fun print(lines: List<PrintLine>, createdAt: Long, id: String) {
        try {
            val printerOutputStream = PrinterOutputStream(PRINT_SERVICE)
            val escpos = EscPos(printerOutputStream)

            val format = SimpleDateFormat("EEEE MMMM dd, yyyy. HH:mm a")
            val algorithm: Bitonal = BitonalThreshold()

            escpos
                .apply {
                    lines.forEach { line ->
                        when (line) {
                            is PrintText -> {
                                val (underline, bold, justification, fontSize, font, whiteOnBlack) =
                                    line.options

                                val style =
                                    Style()
                                        .setUnderline(
                                            if (underline) Style.Underline.TwoDotThick
                                            else Style.Underline.None_Default
                                        )
                                        .setBold(bold)
                                        .setJustification(escPosJustification(justification))
                                        .setFontSize(
                                            escPosFontSize(fontSize),
                                            escPosFontSize(fontSize)
                                        )
                                        .setColorMode(
                                            if (whiteOnBlack) Style.ColorMode.WhiteOnBlack
                                            else Style.ColorMode.BlackOnWhite_Default
                                        )
                                        .setFontName(escPosFont(font))

                                line.content.forEach { lines -> writeLF(style, lines) }
                                if (line.feed != 0) feed(line.feed)
                            }
                            is PrintImage -> {
                                val escposImage =
                                    EscPosImage(
                                        CoffeeImageImpl(ImageIO.read(URL(line.image))),
                                        algorithm
                                    )

                                val imageWrapper =
                                    RasterBitImageWrapper()
                                        .setJustification(escPosJustification(line.justification))
                                        .setRasterBitImageMode(
                                            RasterBitImageWrapper.RasterBitImageMode.Normal_Default
                                        )

                                write(imageWrapper, escposImage)
                                if (line.feed != 0) feed(line.feed)
                            }
                            is PrintQrCode -> {
                                val qrCode =
                                    QRCode()
                                        .setSize(line.size)
                                        .setJustification(escPosJustification(line.justification))

                                write(qrCode, line.link)
                                if (line.feed != 0) feed(line.feed)
                            }
                            is SeparatePrintText -> {
                                val (underline, bold, justification, fontSize, font, whiteOnBlack) =
                                    line.options

                                val style =
                                    Style()
                                        .setUnderline(
                                            if (underline) Style.Underline.TwoDotThick
                                            else Style.Underline.None_Default
                                        )
                                        .setBold(bold)
                                        .setJustification(escPosJustification(justification))
                                        .setFontSize(
                                            escPosFontSize(fontSize),
                                            escPosFontSize(fontSize)
                                        )
                                        .setColorMode(
                                            if (whiteOnBlack) Style.ColorMode.WhiteOnBlack
                                            else Style.ColorMode.BlackOnWhite_Default
                                        )
                                        .setFontName(escPosFont(font))

                                line.formContent().forEach { lines -> writeLF(style, lines) }
                                if (line.feed != 0) feed(line.feed)
                            }
                        }
                    }
                }
                .writeLF(CENTER_JUSTIFY, format.format(Date.from(Instant.ofEpochMilli(createdAt))))
                .writeLF(CENTER_JUSTIFY, id)
                .feed(4)
                .cut(EscPos.CutMode.FULL)

            escpos.close()
        } catch (ex: IOException) {
            LOGGER.error("There was an issue attempting to print.", ex)
        }

        LOGGER.info("Print successful: $id")
    }

    /** Mute MongoDB and start. */
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        val parser = ArgParser("printer-controller")

        val mongodb by parser.argument(ArgType.String, "mongodb", "MongoDB password")
        val printer by parser.argument(ArgType.String, "printer", "Printer name")

        val testPrint by
            parser.option(
                ArgType.Boolean,
                fullName = "testPrint",
                shortName = "testPrint",
                description = "Attempt a print on connection."
            )

        parser.parse(args)

        val loggerContext: LoggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
        val rootLogger: Logger = loggerContext.getLogger("org.mongodb.driver")
        rootLogger.level = Level.OFF

        val printers = PrinterOutputStream.getListPrintServicesNames()
        if (!printers.contains(printer)) {
            LOGGER.error(
                "FATAL, invalid printer: $printer (Valid Printers: ${printers.joinToString(", ")})"
            )
            exitProcess(-1)
        }

        PRINT_SERVICE = PrinterOutputStream.getPrintServiceByName(printer)

        LOGGER.debug("Using printer: $printer")

        if (testPrint == true) {
            LOGGER.debug("Doing a test print")

            EscPos(PrinterOutputStream(PRINT_SERVICE))
                .writeLF("Test Print")
                .feed(8)
                .writeLF("Goodbye!")
                .cut(EscPos.CutMode.FULL)
                .close()
        }

        val db =
            KMongo.createClient(
                "mongodb+srv://printerController:${mongodb}@ajknpr.hscnn.mongodb.net/myFirstDatabase?retryWrites=true&w=majority"
            )

        while (true) {
            delay(15000)
            val col = db.getDatabase("printer").getCollection<PrinterData>("queue")

            LOGGER.debug("Performing MongoDB check")

            val li = col.find().toList()

            li.forEach { doc ->
                LOGGER.debug("Found print!")

                when (doc) {
                    is PrintRequest -> {
                        print(doc.payload)
                        col.deleteOne(PrintRequest::payload eq doc.payload)
                    }
                    is LargePrintRequest -> {
                        doc.payload.forEach(Controller::print)
                        col.deleteOne(LargePrintRequest::payload eq doc.payload)
                    }
                }

                db.getDatabase("printer")
                    .getCollection<ArchivedPrintRequest>("archive")
                    .insertOne(ArchivedPrintRequest(doc))
            }
        }
    }
}
