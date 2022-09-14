package seamcarving

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.util.*
import java.util.Comparator.*
import javax.imageio.ImageIO
import kotlin.math.*

data class Pixel(val y: Int, val x: Int)

abstract class SeamCarving {
    fun removeVerticalSeams(origImage: BufferedImage, count: Int): BufferedImage {
        var image = origImage
        repeat(count) {
            val seam = verticalSeam(image)
            image = image.removeVerticalSeam(seam)
        }
        return image
    }

    fun removeHorizontalSeams(origImage: BufferedImage, count: Int): BufferedImage {
        var image = origImage
        repeat(count) {
            val seam = horizontalSeam(image)
            image = image.removeHorizontalSeam(seam)
        }
        return image
    }

    private fun BufferedImage.removeVerticalSeam(seam: Sequence<Pixel>): BufferedImage {
        val targetImage = BufferedImage(width - 1, height, type)

        for ((y, x) in seam) {
            for (cx in 0 until width) {
                val rgb = getRGB(cx, y)
                if (cx == x) continue

                val tx = if (cx < x) cx else cx - 1
                targetImage.setRGB(tx, y, rgb)
            }
        }

        return targetImage
    }

    private fun BufferedImage.removeHorizontalSeam(seam: Sequence<Pixel>): BufferedImage {
        val targetImage = BufferedImage(width, height - 1, type)

        for ((y, x) in seam) {
            for (cy in 0 until height) {
                val rgb = getRGB(x, cy)
                if (cy == y) continue

                val ty = if (cy < y) cy else cy - 1
                targetImage.setRGB(x, ty, rgb)
            }
        }

        return targetImage
    }

    protected abstract fun verticalSeam(image: BufferedImage): Sequence<Pixel>

    private fun horizontalSeam(image: BufferedImage): Sequence<Pixel> =
        verticalSeam(image.transposed()).map { Pixel(it.x, it.y) }

    protected fun BufferedImage.energy(x: Int, y: Int): Double {
        return sqrt(gradX(x, y) + gradY(x, y))
    }

    private fun BufferedImage.transposed(): BufferedImage {
        val outImage = BufferedImage(this.height, this.width, this.type)
        for (y in 0 until this.height) {
            for (x in 0 until this.width) {
                val color = getRGB(x, y)
                outImage.setRGB(y, x, color)
            }
        }
        return outImage
    }

    private fun BufferedImage.gradX(x: Int, y: Int): Double {
        val nx = max(1, min(x, width - 2))
        val lo = Color(getRGB(nx - 1, y))
        val hi = Color(getRGB(nx + 1, y))
        return grad(lo, hi)
    }

    private fun BufferedImage.gradY(x: Int, y: Int): Double {
        val ny = max(1, min(y, height - 2))
        val lo = Color(getRGB(x, ny - 1))
        val hi = Color(getRGB(x, ny + 1))
        return grad(lo, hi)
    }

    private fun grad(c1: Color, c2: Color): Double =
        (c1.red - c2.red).squared() + (c1.green - c2.green).squared() + (c1.blue - c2.blue).squared()

    private fun Int.squared(): Double = 1.0 * this * this
}

class DPSeamCarving : SeamCarving() {
    override fun verticalSeam(image: BufferedImage): Sequence<Pixel> {
        val energy = sumEnergyMatrix(image)
        return seam(energy)
    }

    private fun sumEnergyMatrix(image: BufferedImage): Array<DoubleArray> {
        val m = image.height
        val n = image.width

        val energy = Array(m) { DoubleArray(n) }
        for (y in 0 until m) {
            for (x in 0 until n) {
                energy[y][x] = image.energy(x, y)
                if (y > 0) {
                    var emin = energy[y - 1][x]

                    if (x > 0) emin = min(emin, energy[y - 1][x - 1])
                    if (x < n - 1) emin = min(emin, energy[y - 1][x + 1])

                    energy[y][x] += emin
                }
            }
        }
        return energy
    }

    private fun seam(energy: Array<DoubleArray>) = sequence {
        val m = energy.size
        val n = energy[0].size

        var x = 0
        for (currx in 1 until n) {
            if (energy[m - 1][currx] < energy[m - 1][x]) {
                x = currx
            }
        }

        for (y in m - 1 downTo 1) {
            yield(Pixel(y, x))

            var minx = x
            if (x > 0 && energy[y - 1][x - 1] < energy[y - 1][minx]) minx = x - 1
            if (x < n - 1 && energy[y - 1][x + 1] < energy[y - 1][minx]) minx = x + 1
            x = minx
        }

        yield(Pixel(0, x))
    }
}

class DijkstraSeamCarving : SeamCarving() {
    companion object {
        private const val MAX: Double = Double.MAX_VALUE / 2
    }

    override fun verticalSeam(image: BufferedImage): Sequence<Pixel> {
        val energy = energyMatrix(image)
        return seam(energy)
    }

    private fun energyMatrix(image: BufferedImage): Array<DoubleArray> {
        val m = image.height + 2 // add 2 extra horizontal lines with zero energy
        val n = image.width
        val energy = Array(m) { DoubleArray(n) }
        for (x in 0 until n) {
            for (y in 0 until m - 2) {
                energy[y + 1][x] = image.energy(x, y)
            }
        }
        return energy
    }

    private fun seam(matrix: Array<DoubleArray>): Sequence<Pixel> {
        val m = matrix.size
        val n = matrix[0].size

        val distTo = DoubleArray(m * n) { MAX }
        val parent = IntArray(m * n) { -1 }
        distTo[0] = 0.0

        val pq = PriorityQueue<Pair<Int, Double>>(comparingDouble { it.second })

        pq.add(Pair(0, 0.0))
        while (!pq.isEmpty()) {
            val (cell, _) = pq.poll()

            if (cell == m * n - 1)
                break

            for (nei in neighbors(cell, m, n)) {
                val neiRow = nei / n
                val neiCol = nei % n
                if (distTo[nei] > distTo[cell] + matrix[neiRow][neiCol]) {
                    distTo[nei] = distTo[cell] + matrix[neiRow][neiCol]
                    parent[nei] = cell
                    pq.add(Pair(nei, distTo[nei]))
                }
            }
        }

        return path(parent, m, n)
    }

    private fun path(parent: IntArray, m: Int, n: Int) = sequence {
        var tail = parent[parent.lastIndex]

        while (tail != 0) {
            val row = tail / n
            val col = tail % n

            if (row in 1 until m - 1) {
                yield(Pixel(row - 1, col))
            }

            tail = parent[tail]
        }
    }

    private fun neighbors(cell: Int, m: Int, n: Int) = sequence {
        val row = cell / n
        val col = cell % n

        if ((row == 0 || row == m - 1)) {
            if (col > 0) yield(cell - 1)
            if (col + 1 < n) yield(cell + 1)
        }

        if (row + 1 < m) {
            if (col > 0) yield(cell + n - 1)
            yield(cell + n)
            if (col + 1 < n) yield(cell + n + 1)
        }
    }
}

fun main(args: Array<String>) {
    val inPath = readArg(args, "-in")
    val outPath = readArg(args, "-out")
    val verSeams = readArg(args, "-width").toInt()
    val horSeams = readArg(args, "-height").toInt()

    val image = ImageIO.read(File(inPath))

    //val seamCarving = DijkstraSeamCarving()
    val seamCarving = DPSeamCarving()

    val outImage = image.reduce(seamCarving, verSeams, horSeams)

    ImageIO.write(outImage, "png", File(outPath))
}

fun BufferedImage.reduce(seamCarving: SeamCarving, verSeams: Int, horSeams: Int): BufferedImage {
    var image = this

    image = seamCarving.removeVerticalSeams(image, verSeams)
    image = seamCarving.removeHorizontalSeams(image, horSeams)

    return image
}

fun readArg(args: Array<String>, arg: String, default: String = ""): String {
    return when (val argIndex = args.indexOf(arg)) {
        in 0 until args.lastIndex -> args[argIndex + 1]
        else -> default
    }
}
