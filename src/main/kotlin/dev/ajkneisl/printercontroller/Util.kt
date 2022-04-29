package dev.ajkneisl.printercontroller

import com.github.anastaciocintra.escpos.Style
import dev.ajkneisl.printerlib.Justification

/** Form the [justification] into an EscPos compatible type. */
fun escPosJustification(
    justification: Justification
): com.github.anastaciocintra.escpos.EscPosConst.Justification {
    return when (justification) {
        Justification.LEFT ->
            com.github.anastaciocintra.escpos.EscPosConst.Justification.Left_Default
        Justification.CENTER -> com.github.anastaciocintra.escpos.EscPosConst.Justification.Center
        Justification.RIGHT -> com.github.anastaciocintra.escpos.EscPosConst.Justification.Right
    }
}

/** Form [font] into EscPos compatible type. */
fun escPosFont(font: Int): Style.FontName {
    return when (font) {
        0 -> Style.FontName.Font_A_Default
        1 -> Style.FontName.Font_B
        2 -> Style.FontName.Font_C
        else -> throw Exception()
    }
}

/** Form [fontSize] into EscPos compatible type. */
fun escPosFontSize(fontSize: Int): Style.FontSize {
    return when (fontSize) {
        1 -> Style.FontSize._1
        2 -> Style.FontSize._2
        3 -> Style.FontSize._3
        4 -> Style.FontSize._4
        5 -> Style.FontSize._5
        6 -> Style.FontSize._6
        7 -> Style.FontSize._7
        8 -> Style.FontSize._8
        else -> throw Exception()
    }
}