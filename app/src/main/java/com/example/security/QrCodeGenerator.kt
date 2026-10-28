package com.example.security

/**
 * Self-contained QR Code (Model 2, Version 3, EC-M, Byte mode) Matrix Generator.
 * Matrix size: 29x29. Perfectly formatted for Emergency Kit secret keys.
 */
object QrCodeGenerator {

    private const val SIZE = 29
    private const val TOTAL_CODEWORDS = 70
    private const val DATA_CODEWORDS = 44
    private const val EC_CODEWORDS = 26

    // GF(256) log and exp tables for polynomial arithmetic (mod 0x11D / 285)
    private val EXP_TABLE = IntArray(256)
    private val LOG_TABLE = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            EXP_TABLE[i] = x
            LOG_TABLE[x] = i
            x = (x shl 1)
            if (x >= 256) {
                x = x xor 285
            }
        }
        for (i in 255 until 512) {
            // allows indexing without modulo in multiplications
        }
    }

    private fun gfMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return EXP_TABLE[(LOG_TABLE[a] + LOG_TABLE[b]) % 255]
    }

    /**
     * Encodes ASCII text into a 29x29 boolean QR code matrix.
     */
    fun encode(text: String): Array<BooleanArray> {
        val matrix = Array(SIZE) { BooleanArray(SIZE) }
        val isReserved = Array(SIZE) { BooleanArray(SIZE) }

        // 1. Place finder patterns
        placeFinder(matrix, isReserved, 0, 0)
        placeFinder(matrix, isReserved, SIZE - 7, 0)
        placeFinder(matrix, isReserved, 0, SIZE - 7)

        // 2. Alignment pattern at (22, 22) for Version 3
        placeAlignment(matrix, isReserved, 22, 22)

        // 3. Timing patterns
        for (i in 8 until SIZE - 8) {
            val bit = (i % 2 == 0)
            if (!isReserved[6][i]) {
                matrix[6][i] = bit
                isReserved[6][i] = true
            }
            if (!isReserved[i][6]) {
                matrix[i][6] = bit
                isReserved[i][6] = true
            }
        }

        // 4. Reserve format information areas
        for (i in 0..8) {
            isReserved[8][i] = true
            isReserved[i][8] = true
            isReserved[8][SIZE - 1 - i] = true
            isReserved[SIZE - 1 - i][8] = true
        }
        matrix[SIZE - 8][8] = true // Dark module
        isReserved[SIZE - 8][8] = true

        // 5. Data encoding (Byte mode: indicator 0100, 8-bit count, data, pad)
        val dataBytes = text.toByteArray(Charsets.ISO_8859_1)
        val bitBuffer = mutableListOf<Boolean>()

        // Mode indicator 0100
        bitBuffer.addAll(listOf(false, true, false, false))

        // Character count indicator (8 bits for Version 1-9)
        val count = dataBytes.size
        for (i in 7 downTo 0) {
            bitBuffer.add(((count shr i) and 1) == 1)
        }

        // Data
        for (b in dataBytes) {
            val v = b.toInt() and 0xFF
            for (i in 7 downTo 0) {
                bitBuffer.add(((v shr i) and 1) == 1)
            }
        }

        // Terminator up to 4 zeroes
        val padTarget = DATA_CODEWORDS * 8
        val terminatorLen = minOf(4, padTarget - bitBuffer.size)
        for (i in 0 until terminatorLen) {
            bitBuffer.add(false)
        }

        // Pad to byte boundary
        while (bitBuffer.size % 8 != 0) {
            bitBuffer.add(false)
        }

        // Pad codewords alternating 0xEC (11101100) and 0x11 (00010001)
        val padBytes = intArrayOf(0xEC, 0x11)
        var padIndex = 0
        while (bitBuffer.size < padTarget) {
            val padByte = padBytes[padIndex % 2]
            for (i in 7 downTo 0) {
                bitBuffer.add(((padByte shr i) and 1) == 1)
            }
            padIndex++
        }

        // Group into data codewords
        val dataCodewords = IntArray(DATA_CODEWORDS)
        for (i in 0 until DATA_CODEWORDS) {
            var byteVal = 0
            for (b in 0 until 8) {
                if (bitBuffer[i * 8 + b]) {
                    byteVal = byteVal or (1 shl (7 - b))
                }
            }
            dataCodewords[i] = byteVal
        }

        // 6. Reed-Solomon Error Correction (26 EC codewords)
        val generator = rsGeneratorPolynomial(EC_CODEWORDS)
        val ecCodewords = rsComputeRemainder(dataCodewords, generator, EC_CODEWORDS)

        val finalCodewords = dataCodewords + ecCodewords
        val finalBits = mutableListOf<Boolean>()
        for (cw in finalCodewords) {
            for (b in 7 downTo 0) {
                finalBits.add(((cw shr b) and 1) == 1)
            }
        }

        // 7. Place data bits into matrix in two-column zig-zag right to left
        var bitIndex = 0
        var x = SIZE - 1
        var goingUp = true

        while (x > 0) {
            if (x == 6) x-- // Skip vertical timing pattern column

            val yRange = if (goingUp) (SIZE - 1 downTo 0) else (0 until SIZE)
            for (y in yRange) {
                for (col in intArrayOf(x, x - 1)) {
                    if (!isReserved[y][col]) {
                        var bit = if (bitIndex < finalBits.size) finalBits[bitIndex++] else false
                        // Mask pattern 0: (row + col) % 2 == 0
                        if ((y + col) % 2 == 0) {
                            bit = !bit
                        }
                        matrix[y][col] = bit
                    }
                }
            }
            goingUp = !goingUp
            x -= 2
        }

        // 8. Place format bits (EC Level M = 00, Mask 0 = 000 -> 00000, BCH code: 101010000010010)
        val formatBits = intArrayOf(1, 0, 1, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 1, 0)
        // Around top-left finder
        var fIndex = 0
        for (i in 0..5) matrix[8][i] = formatBits[fIndex++] == 1
        matrix[8][7] = formatBits[fIndex++] == 1
        matrix[8][8] = formatBits[fIndex++] == 1
        matrix[7][8] = formatBits[fIndex++] == 1
        for (i in 5 downTo 0) matrix[i][8] = formatBits[fIndex++] == 1

        // Split around top-right and bottom-left
        fIndex = 0
        for (i in 0..6) matrix[SIZE - 1 - i][8] = formatBits[fIndex++] == 1
        for (i in 7..14) matrix[8][SIZE - 15 + i] = formatBits[fIndex++] == 1

        return matrix
    }

    private fun placeFinder(matrix: Array<BooleanArray>, isReserved: Array<BooleanArray>, startX: Int, startY: Int) {
        for (dy in -1..7) {
            for (dx in -1..7) {
                val x = startX + dx
                val y = startY + dy
                if (x in 0 until SIZE && y in 0 until SIZE) {
                    val inFinder = dx in 0..6 && dy in 0..6
                    val isBlack = inFinder && (dx == 0 || dx == 6 || dy == 0 || dy == 6 || (dx in 2..4 && dy in 2..4))
                    matrix[y][x] = isBlack
                    isReserved[y][x] = true
                }
            }
        }
    }

    private fun placeAlignment(matrix: Array<BooleanArray>, isReserved: Array<BooleanArray>, centerX: Int, centerY: Int) {
        for (dy in -2..2) {
            for (dx in -2..2) {
                val x = centerX + dx
                val y = centerY + dy
                val isBlack = (dx == -2 || dx == 2 || dy == -2 || dy == 2 || (dx == 0 && dy == 0))
                matrix[y][x] = isBlack
                isReserved[y][x] = true
            }
        }
    }

    private fun rsGeneratorPolynomial(ecLength: Int): IntArray {
        var g = intArrayOf(1)
        for (i in 0 until ecLength) {
            val factor = intArrayOf(1, EXP_TABLE[i])
            val newG = IntArray(g.size + 1)
            for (j in g.indices) {
                newG[j] = newG[j] xor gfMul(g[j], factor[0])
                newG[j + 1] = newG[j + 1] xor gfMul(g[j], factor[1])
            }
            g = newG
        }
        return g
    }

    private fun rsComputeRemainder(data: IntArray, generator: IntArray, ecLength: Int): IntArray {
        val remainder = IntArray(ecLength)
        for (d in data) {
            val factor = d xor remainder[0]
            System.arraycopy(remainder, 1, remainder, 0, ecLength - 1)
            remainder[ecLength - 1] = 0
            for (i in 0 until ecLength) {
                remainder[i] = remainder[i] xor gfMul(generator[i + 1], factor)
            }
        }
        return remainder
    }
}
