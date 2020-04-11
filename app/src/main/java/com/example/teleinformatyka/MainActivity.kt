package com.example.teleinformatyka

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.Html
import android.text.TextWatcher
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import java.lang.Math.pow

@ExperimentalUnsignedTypes
@ExperimentalStdlibApi
class MainActivity : AppCompatActivity() {

    /**
     * Main code block
     */
    private var crc32Code = 0x04c11db7
    private var crc16Code = 0x1021
    private var crc16CodeReverse = 0x8005
    private var currentSelectedCRCMode = 2
    private var crc32Table: UIntArray = uintArrayOf()
    private var crc16Table: UShortArray = ushortArrayOf()
    private var crc16TableReverse: UShortArray = ushortArrayOf()
    private lateinit var byteArray: ByteArray
    private var crcCode = 0u
    private var hammingList = mutableListOf<String>()
    private var hammingListCopy = mutableListOf<String>()
    private var powerList = mutableListOf<Int>()
    private var flippedList = mutableListOf<Int>()
    private var fixedList = mutableListOf<Int>()
    private var goodString = " <font color='#00FF00'>Dobrze</font>"
    private var badString = " <font color='#FF0000'>Źle</font>"
    private var displayColorString = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        init()
        setListeners()
    }

    /**
     * Calculating crc
     */

    private fun calculateCrc(): UInt {
        return when (currentSelectedCRCMode) {
            0 -> calculateCrc16()
            1 -> calculateCrc16Reverse()
            2 -> calculateCrc32()
            else -> 0.toUInt()
        }
    }

    private fun calculateCrc16(): UInt {
        var crc: UShort = 0x0000u
        byteArray.forEach { byte ->
            val curByte = byte.toUInt()
            crc = (crc.toUInt() xor (curByte.toUInt() shl 8)).toUShort()
            val pos = (crc.toUInt() shr 8)
            crc = (crc.toUInt() shl 8).toUShort()
            crc = (crc xor crc16Table[pos.toInt()])
        }
        return crc.toUInt()
    }

    private fun calculateCrc16Reverse(): UInt {
        var crc: UShort = 0x0000u
        byteArray.forEach { byte ->
            val curByte = reflect8(byte.toUInt())
            crc = (crc.toUInt() xor (curByte.toUInt() shl 8)).toUShort()
            crc =
                ((crc.toUInt() shl 8).toUShort()) xor crc16TableReverse[(crc.toUInt() shr 8).toInt()]
        }
        return reflect16(crc.toUInt()).toUInt()
    }

    private fun calculateCrc32(): UInt {
        var crc: UInt = 0xFFFFFFFFu
        byteArray.forEach { byte ->
            val curByte = reflect8(byte.toUInt())
            crc = (crc xor (curByte.toUInt() shl 24)).toUInt()
            val pos = (crc shr 24)
            crc = (crc shl 8)
            crc = (crc xor crc32Table[pos.toInt()])
        }
        crc = reflect32(crc)
        return crc.inv()
    }

    /**
     * calculating hamming code
     */

    private fun calculateHamming() {
        byteArray.forEach { byte ->
            byte.toString(2).padStart(8, '0').toCharArray().forEach {
                hammingList.add(it.toString())
            }
        }
        crcCode.toString(2).padStart(giveSize(), '0').toCharArray().forEach {
            hammingList.add(it.toString())
        }
        var redundantBitsCount = generateSequence(2) {
            it + 1
        }
            .first { r ->
                hammingList.size + r + 1 <= (1 shl r)
            } + hammingList.size

        for (i in 0..redundantBitsCount - hammingList.size - 1) {
            hammingList.add(pow(2.0, i.toDouble()).toInt() - 1, "5")
            powerList.add(pow(2.0, i.toDouble()).toInt() - 1)
        }
        powerList.forEach {
            Log.d("qpapappapapapapapapa", hammingList.toString())
            hammingList[it] = checkParityBitValue(it)
        }

        var tempString = displayColors(mutableListOf(), mutableListOf())
        inputDataHamming.text = Html.fromHtml(tempString)
        outputDataError.text = Html.fromHtml(tempString)
        hammingListCopy.addAll(hammingList)
    }

    private fun checkParityBitValue(start: Int) = generateSequence(start) {
        it + 1
    }
        .take(hammingList.size - start)
        .filterIndexed { i,
                         _ ->
            i % ((2 * (start + 1))) < start + 1
        }
        .drop(1) // ignore the parity bit
        .map { hammingList[it].toInt() }
        .reduce { a, b -> a xor b }
        .toString()

    /**
     * decoding and fixing hamming
     */

    private fun checkHamming() {
        indexesOfInvalidParityBits().let { result ->
            when (result.isEmpty()) {
                true -> hammingList
                false -> correctHamming(result.sum() - 1)
            }
        }
        var tempString = displayColors(flippedList, fixedList)
        var temp = hammingList.joinToString("").padStart(hammingList.size, '0')
        if (temp == inputDataHamming.text.toString()) {
            outputDataHamming.text = Html.fromHtml(tempString + goodString)
        } else {
            outputDataHamming.text = Html.fromHtml(tempString + badString)
        }
    }

    private fun correctHamming(result: Int): MutableList<String> {
        flipBit(hammingList, result)
        return hammingList
    }

    private fun indexesOfInvalidParityBits(): List<Int> {
        fun toValidationResult(it: Int): Pair<Int,
                Boolean> =
            (checkParityBitValue(it - 1).toInt() xor hammingList[it - 1].toInt()).toString()
                .let { r ->
                    it to (r == "0")
                }
        return generateSequence(1) {
            it * 2
        }
            .takeWhile {
                it < hammingList.size
            }
            .map {
                toValidationResult(it)
            }
            .filter {
                !it.second
            }
            .map {
                it.first
            }
            .toList()
    }

    private fun removeHammingBits() {
        powerList.reversed().forEach {
            hammingList.removeAt(it)
        }
    }

    private fun removeCrcBits() {
        hammingList = hammingList.take(hammingList.size - giveSize()).toMutableList()
    }

    /**
     * checking CRC
     */

    private fun checkCrc() {
        var temp = hammingList.joinToString("").padStart(hammingList.size, '0')
            .take(hammingList.size - giveSize())
        var byteString = ""
        for (i in 0 until temp.length / 8) {
            byteString += Integer.parseInt(temp.substring(i * 8, (i + 1) * 8), 2).toChar()
                .toString()
        }
        byteArray = byteString.toByteArray()
        if (crcCode == calculateCrc()) {
            outputDataCRC.text = Html.fromHtml(
                hammingList.joinToString("").padStart(
                    hammingList.size,
                    '0'
                ) + goodString
            )
        } else {
            outputDataCRC.text = Html.fromHtml(
                hammingList.joinToString("").padStart(
                    hammingList.size,
                    '0'
                ) + badString
            )
        }
    }

    /**
     *  General utils
     */

    private fun calculateCrc16Table() {
        crc16Table = UShortArray(256)
        for (divident in 0..255) {
            var currByte = (divident.toByte().toUInt() shl 8).toUShort()
            for (bit in 0..7) {
                currByte = if ((currByte and 0x8000u).toString(2).take(1).equals("1")) {
                    ((currByte.toUInt() shl 1) xor crc16Code.toUInt()).toUShort()
                } else {
                    (currByte.toUInt() shl 1).toUShort()
                }
            }
            crc16Table[divident] = currByte
        }
    }

    private fun calculateCrc16ReverseTable() {
        crc16TableReverse = UShortArray(256)
        for (divident in 0..255) {
            var currByte = reflect8(divident.toUInt()).toUShort()
            for (bit in 0..7) {
                currByte = if ((currByte and 0x0001u).toString(2).takeLast(1).equals("1")) {
                    ((currByte.toUInt() shr 1) xor reflect16(crc16CodeReverse.toUInt()).toUInt()).toUShort()
                } else {
                    (currByte.toUInt() shr 1).toUShort()
                }
            }
            crc16TableReverse[divident] = reflect16(currByte.toUInt())
        }
    }

    private fun calculateCrc32Table() {
        crc32Table = UIntArray(256)
        for (divident in 0..255) {
            var currByte = divident.toByte().toUInt() shl 24
            for (bit in 0..7) {
                currByte = if ((currByte and 0x80000000u).toString(2).take(1).equals("1")) {
                    (currByte shl 1) xor crc32Code.toUInt()
                } else {
                    (currByte shl 1)
                }
            }
            crc32Table[divident] = currByte
        }
    }

    private fun reflect8(value: UInt): UByte {
        var resByte: UByte = 0u
        for (i in 0..7) {
            if ((value and (1.toUInt() shl i)).toString(2).take(1).equals("1")) {
                resByte = resByte or (1 shl 7 - i).toUByte()
            }
        }
        return resByte
    }

    private fun reflect16(value: UInt): UShort {
        var resVal: UShort = 0u
        for (i in 0..15) {
            if ((value and (1.toUInt() shl i)).toString(2).take(1).equals("1")) {
                resVal = resVal or (1 shl 15 - i).toUShort()
            }
        }
        return resVal
    }

    private fun reflect32(value: UInt): UInt {
        var resVal: UInt = 0u
        for (i in 0..31) {
            if ((value and (1.toUInt() shl i)).toString(2).take(1).equals("1")) {
                resVal = resVal or (1 shl 31 - i).toUInt()
            }
        }
        return resVal
    }

    private fun giveSize(): Int {
        when (currentSelectedCRCMode) {
            0 -> return 16
            1 -> return 16
            2 -> return 32
        }
        return 0
    }

    private fun displayColors(flippedList: MutableList<Int>, fixedList: MutableList<Int>): String {
        displayColorString = ""
        hammingList.forEachIndexed { index, s ->
            displayColorString += if (index > (hammingList.size - giveSize() - powerList.filter { it > hammingList.size - giveSize() }.size - 1) && index in powerList && index in flippedList && index in fixedList) {
                "<font color='#0000FF'><b>$s</b></font>"
            } else if (index > (hammingList.size - giveSize() - powerList.filter { it > hammingList.size - giveSize() }.size - 1) && index in powerList && index in flippedList) {
                "<font color='#D2691E'><b>$s</b></font>"
            } else if (index > (hammingList.size - giveSize() - powerList.filter { it > hammingList.size - giveSize() }.size - 1) && index in powerList && index in fixedList) {
                "<font color='#0000FF'><b>$s</b></font>"
            } else if (index > (hammingList.size - giveSize() - powerList.filter { it > hammingList.size - giveSize() }.size - 1) && index in fixedList && index in flippedList) {
                "<font color='#0000FF'><b>$s</b></font>"
            } else if (index > (hammingList.size - giveSize() - powerList.filter { it > hammingList.size - giveSize() }.size - 1) && index in fixedList) {
                "<font color='#0000FF'><b>$s</b></font>"
            } else if (index > (hammingList.size - giveSize() - powerList.filter { it > hammingList.size - giveSize() }.size - 1) && index in flippedList) {
                "<font color='#00FF00'><b>$s</b></font>"
            } else if (index > (hammingList.size - giveSize() - powerList.filter { it > hammingList.size - giveSize() }.size - 1) && index in powerList) {
                "<font color='#00FF00'><b>$s</b></font>"
            } else if (index in fixedList) {
                "<font color='#0000FF'>$s</font>"
            }  else if (index in flippedList) {
                "<font color='#D2691E'>$s</font>"
            } else if (index in powerList) {
                "<font color='#00FF00'>$s</font>"
            } else if (index > (hammingList.size - giveSize() - powerList.filter { it > hammingList.size - giveSize() }.size - 1)) {
                "<font color='#000000'><b>$s</b></font>"
            } else {
                s
            }
        }
        return displayColorString
    }

    private fun resetData() {
        byteArray = if (inputData.text.toString() != "") {
            inputData.text.toString().toByteArray()
        } else {
            byteArrayOf()
        }
        inputDataBinary.text = ""
        inputDataHamming.text = ""
        inputDataCRC.text = ""
        outputData.text = ""
        outputDataError.text = ""
        outputDataCRC.text = ""
        outputDataHamming.text = ""
        powerList.clear()
        flippedList.clear()
        fixedList.clear()
        hammingList.clear()
        hammingListCopy.clear()
        byteArray.forEach { byte ->
            inputDataBinary.append(byte.toString(2).padStart(8, '0') + ".")
        }
    }

    private fun init() {
        calculateCrc32Table()
        calculateCrc16Table()
        calculateCrc16ReverseTable()
        CRC32Button.setBackgroundColor(Color.GRAY)
        CRC16Button.setBackgroundColor(Color.WHITE)
        CRC16ReverseButton.setBackgroundColor(Color.WHITE)
    }

    private fun performCalculations() {
        crcCode = calculateCrc()
        inputDataCRC.text =
            Html.fromHtml("<b>" + crcCode.toString(2).padStart(giveSize(), '0') + "</b>")
        calculateHamming()
        checkHamming()
        removeHammingBits()
        checkCrc()
        removeCrcBits()
        outputData.text = hammingList.joinToString("").padStart(hammingList.size, '0')
    }

    private fun setListeners() {
        inputData.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                resetData()
                performCalculations()
            }
        })

        CRC16Button.setOnClickListener {
            currentSelectedCRCMode = 0
            it.setBackgroundColor(Color.GRAY)
            CRC16ReverseButton.setBackgroundColor(Color.WHITE)
            CRC32Button.setBackgroundColor(Color.WHITE)
            resetData()
            performCalculations()
        }
        CRC16ReverseButton.setOnClickListener {
            currentSelectedCRCMode = 1
            it.setBackgroundColor(Color.GRAY)
            CRC16Button.setBackgroundColor(Color.WHITE)
            CRC32Button.setBackgroundColor(Color.WHITE)
            resetData()
            performCalculations()
        }
        CRC32Button.setOnClickListener {
            currentSelectedCRCMode = 2
            it.setBackgroundColor(Color.GRAY)
            CRC16Button.setBackgroundColor(Color.WHITE)
            CRC16ReverseButton.setBackgroundColor(Color.WHITE)
            resetData()
            performCalculations()
        }

        acceptErrorBitsButton.setOnClickListener {
            hammingList.clear()
            hammingList.addAll(hammingListCopy)
            if (errorBit1.text.toString() != "" && errorBit1.text.toString().toInt() < hammingList.size) {
                flipBit(hammingList, hammingListCopy, errorBit1.text.toString().toInt())
            }
            if (errorBit2.text.toString() != "" && errorBit2.text.toString().toInt() < hammingList.size) {
                flipBit(hammingList, hammingListCopy, errorBit2.text.toString().toInt())
            }
            if (errorBit3.text.toString() != "" && errorBit3.text.toString().toInt() < hammingList.size) {
                flipBit(hammingList, hammingListCopy, errorBit3.text.toString().toInt())
            }
            if (errorBit4.text.toString() != "" && errorBit4.text.toString().toInt() < hammingList.size) {
                flipBit(hammingList, hammingListCopy, errorBit4.text.toString().toInt())
            }

            outputDataError.text = Html.fromHtml(
                displayColors(flippedList, mutableListOf()).padStart(
                    hammingList.size,
                    '0'
                )
            )
            checkHamming()
            removeHammingBits()
            checkCrc()
            removeCrcBits()
            outputData.text = hammingList.joinToString("").padStart(8, '0')
        }
    }

    private fun flipBit(list: MutableList<String>, index: Int) {
        if(index < list.size) {
            list[index] = (list[index].toInt() xor 1).toString()
            if (fixedList.contains(index)) {
                fixedList.remove(index)
            } else {
                fixedList.add(index)
            }
        }
    }

    private fun flipBit(list: MutableList<String>, listCopy: MutableList<String>, index: Int) {
        list[index] = (list[index].toInt() xor 1).toString()
        listCopy[index] = (listCopy[index].toInt() xor 1).toString()
        if (flippedList.contains(index)) {
            flippedList.remove(index)
        } else {
            flippedList.add(index)
        }
    }
}
