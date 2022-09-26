package processor

import java.util.*
import kotlin.system.exitProcess

class MatrixException(message: String) : Exception(message)

class Vector(size: Int, initValue: Double = 0.0) {
    private val array = DoubleArray(size) { initValue }
    val size = array.size

    operator fun set(index: Int, value: Double) {
        array[index] = value
    }

    operator fun get(index: Int): Double {
        return array[index]
    }
}

enum class TranspositionType {
    DIAGONAL_MAIN,
    DIAGONAL_SIDE,
    LINE_VERTICAL,
    LINE_HORIZONTAL
}

class Matrix(rows: Int, cols: Int) {
    private val mat: Array<Vector> = Array(rows) { Vector(cols) }

    val rows = mat.size
    val cols = mat[0].size

    companion object {
        fun read(promptName: String = ""): Matrix {
            val name = if (promptName.isEmpty()) " " else " $promptName "

            print("Enter size of${name}matrix: ")
            val scanner = Scanner(System.`in`)
            val rows = scanner.nextInt()
            val cols = scanner.nextInt()

            println("Enter${name}matrix: ")
            val matrix = Matrix(rows, cols)
            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    matrix[row, col] = scanner.nextDouble()
                }
            }

            return matrix
        }
    }

    operator fun set(row: Int, col: Int, value: Double) {
        mat[row][col] = value
    }

    operator fun get(row: Int, col: Int): Double {
        return mat[row][col]
    }

    operator fun get(row: Int): Vector {
        return mat[row]
    }

    fun getRow(row: Int) = sequence {
        for (j in 0 until cols) {
            yield(get(row, j))
        }
    }

    fun getCol(col: Int) = sequence {
        for (i in 0 until rows) {
            yield(get(i, col))
        }
    }

    operator fun plus(other: Matrix): Matrix {
        if (rows != other.rows || cols != other.cols) {
            throw IllegalArgumentException("Invalid size")
        }

        val result = Matrix(rows, cols)
        for (i in 0 until rows) {
            for (j in 0 until cols) {
                result[i, j] = get(i, j) + other[i, j]
            }
        }
        return result
    }

    operator fun times(constant: Double): Matrix {
        val result = Matrix(rows, cols)
        for (i in 0 until rows) {
            for (j in 0 until cols) {
                result[i, j] = constant * get(i, j)
            }
        }
        return result
    }

    operator fun times(other: Matrix): Matrix {
        if (cols != other.rows) {
            throw MatrixException("Invalid size")
        }

        val result = Matrix(rows, other.cols)
        for (row in 0 until result.rows) {
            for (col in 0 until result.cols) {
                var sum = 0.0
                for (i in 0 until other.rows) {
                    sum += get(row, i) * other[i][col]
                }
                result[row][col] = sum
            }
        }
        return result
    }

    fun transposed(type: TranspositionType): Matrix {
        return when (type) {
            TranspositionType.DIAGONAL_MAIN -> transposedMain()
            TranspositionType.DIAGONAL_SIDE -> transposedSide()
            TranspositionType.LINE_VERTICAL -> flippedVertically()
            TranspositionType.LINE_HORIZONTAL -> flippedHorizontally()
        }
    }

    fun determinant(): Double {
        if (rows != cols) {
            throw MatrixException("Can't calculate determinant of non-square matrix")
        }

        return when (rows) {
            1 -> mat[0][0]
            2 -> mat[0][0] * mat[1][1] - mat[0][1] * mat[1][0]
            else -> {
                var sum = 0.0
                for (col in 0 until cols) {
                    val minor = minorOf(0, col)
                    val sign = if (col % 2 == 0) 1.0 else -1.0
                    sum += sign * minor.determinant() * mat[0][col]
                }
                return sum
            }
        }
    }

    fun inverse(): Matrix {
        if (rows != cols) {
            throw MatrixException("Can't find inverse of non-square matrix")
        }

        val det = determinant()
        if (det == 0.0) {
            throw MatrixException("This matrix doesn't have an inverse.")
        }

        return adjugate() * (1.0 / det)
    }

    fun cofactor(): Matrix {
        val result = Matrix(cols, rows)
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val sign = if ((row + col) % 2 == 0) 1.0 else -1.0
                result[row][col] = sign * minorOf(row, col).determinant()
            }
        }
        return result
    }

    fun adjugate() = cofactor().transposedMain()

    fun minorOf(row: Int, col: Int): Matrix {
        val minor = Matrix(rows - 1, cols - 1)
        for (i in 0 until rows) {
            if (i == row) continue
            val r = if (i < row) i else i - 1
            for (j in 0 until cols) {
                if (j == col) continue
                val c = if (j < col) j else j - 1
                minor[r, c] = get(i, j)
            }
        }
        return minor
    }

    private fun transposedMain(): Matrix {
        val result = Matrix(cols, rows)
        for (row in 0 until result.rows) {
            for (col in 0 until result.cols) {
                result[col, row] = get(row, col)
            }
        }
        return result
    }

    private fun transposedSide(): Matrix {
        val result = Matrix(cols, rows)
        for (row in 0 until result.rows) {
            for (col in 0 until result.cols) {
                result[col, row] = get(rows - row - 1, cols - col - 1)
            }
        }
        return result
    }

    private fun flippedHorizontally(): Matrix {
        val result = Matrix(rows, cols)
        for (row in 0 until result.rows) {
            for (col in 0 until result.cols) {
                result[row, col] = get(rows - row - 1, col)
            }
        }
        return result
    }

    private fun flippedVertically(): Matrix {
        val result = Matrix(rows, cols)
        for (row in 0 until result.rows) {
            for (col in 0 until result.cols) {
                result[row, col] = get(row, cols - col - 1)
            }
        }
        return result
    }

    override fun toString(): String {
        val sb = StringBuilder()
        for (i in 0 until rows) {
            for (j in 0 until cols) {
                sb.append("%.6f ".format(get(i, j)))
            }
            sb.append("\n")
        }
        return sb.toString()
    }
}

fun printOptions() {
    println("1. Add matrices")
    println("2. Multiply matrix by a constant")
    println("3. Multiply matrices")
    println("4. Transpose matrix")
    println("5. Calculate a determinant")
    println("6. Inverse matrix")
    println("0. Exit")
}

fun printTranspositionOptions() {
    println("1. Main diagonal")
    println("2. Side diagonal")
    println("3. Vertical line")
    println("4. Horizontal line")
}

fun main() {
    while (true) {
        printOptions()
        println("Your choice: ")
        val input = readln()
        val option = input.toIntOrNull() ?: continue
        when (option) {
            1 -> addMatrices()
            2 -> multiplyMatrixByConstant()
            3 -> multiplyMatrices()
            4 -> transposeMatrix()
            5 -> calculateDeterminant()
            6 -> inverseMatrix()
            0 -> exitProcess(0)
        }
    }
}

fun addMatrices() {
    val m1 = Matrix.read("first")
    val m2 = Matrix.read("second")
    println(m1 + m2)
}

fun multiplyMatrixByConstant() {
    val matrix = Matrix.read()
    val constant = readln().toDouble()
    println(matrix * constant)
}

fun multiplyMatrices() {
    val m1 = Matrix.read("first")
    val m2 = Matrix.read("second")
    println(m1 * m2)
}

fun transposeMatrix() {
    while (true) {
        printTranspositionOptions()
        println("Your choice: ")
        val input = readln()
        val option = input.toIntOrNull() ?: continue
        val type = when (option) {
            0 -> exitProcess(0)
            1 -> TranspositionType.DIAGONAL_MAIN
            2 -> TranspositionType.DIAGONAL_SIDE
            3 -> TranspositionType.LINE_VERTICAL
            4 -> TranspositionType.LINE_HORIZONTAL
            else -> continue
        }

        val matrix = Matrix.read()
        val transposedMatrix = matrix.transposed(type)
        println("The result is:")
        println(transposedMatrix)
        break
    }
}

fun calculateDeterminant() {
    val matrix = Matrix.read()
    println("The result is: ")
    println(matrix.determinant())
}

fun inverseMatrix() {
    val matrix = Matrix.read()
    println("The result is: ")
    println(matrix.inverse())
}
