package phonebook

import java.io.File
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeoutException
import kotlin.math.min
import kotlin.math.sqrt

data class PhoneEntry(val name: String, val phone: String)

fun main() {
    val path = Path.of("W:\\Projects\\Kotlin\\data")
    val dictFile = path.resolve("directory.txt").toFile()
    val findFile = path.resolve("find.txt").toFile()

    val phoneBook = readPhoneBook(dictFile)
    val searchNames = readSearchNames(findFile)

    val limit = linearSearch(searchNames, phoneBook.clone())
    println()

    bubbleSortAndJumpSearch(searchNames, phoneBook.clone(), limit.multipliedBy(10))
    println()

    quickSortAndBinarySearch(searchNames, phoneBook.clone())
    println()

    hashTableSearch(searchNames, phoneBook.clone())
    println()
}

fun readPhoneBook(file: File): MutableList<PhoneEntry> {
    val entries = mutableListOf<PhoneEntry>()
    file.bufferedReader().use {
        var line = it.readLine()
        while (line != null) {
            val (phone, name) = line.split(" ", limit = 2);
            entries.add(PhoneEntry(name, phone))

            line = it.readLine()
        }
    }

    return entries
}

private fun readSearchNames(findFile: File): MutableList<String> {
    val searchNames = mutableListOf<String>()
    findFile.bufferedReader().use {
        var name = it.readLine()
        while (name != null) {
            searchNames.add(name)
            name = it.readLine()
        }
    }
    return searchNames
}

private fun linearSearch(searchNames: MutableList<String>, phoneBook: MutableList<PhoneEntry>): Duration {
    println("Start searching (linear search)...")
    var count = 0
    val searchDuration = measure {
        for (name in searchNames) {
            if (phoneBook.linearSearch(name) != null) {
                count++
            }
        }
    }

    println(
        "Found %d / %d entries. Time taken: %s"
            .format(count, searchNames.size, searchDuration.toTimeString())
    )

    return searchDuration
}

fun bubbleSortAndJumpSearch(
    searchNames: MutableList<String>,
    phoneBook: MutableList<PhoneEntry>,
    limit: Duration) {

    var interrupted = false
    println("Start searching (bubble sort + jump search)...")
    val sortDuration = measure {
        try {
            phoneBook.bubbleSort(limit)
        } catch (ext: TimeoutException) {
            interrupted = true
        }
    }

    var found = 0
    val searchDuration = if (interrupted) {
        measure {
            for (name in searchNames) {
                if (phoneBook.linearSearch(name) != null) {
                    found++
                }
            }
        }
    } else {
        measure {
            for (name in searchNames) {
                if (phoneBook.jumpSearch(name) != null) {
                    found++
                }
            }
        }
    }

    println(
        "Found %d / %d entries. Time taken: %s"
            .format(found, searchNames.size, (sortDuration + searchDuration).toTimeString())
    )

    println("Sorting time: %s%s".format(
        sortDuration.toTimeString(),
        if (interrupted) " - STOPPED, moved to linear search" else "")
    )

    println("Searching time: %s".format(searchDuration.toTimeString()))
}

fun quickSortAndBinarySearch(searchNames: MutableList<String>, phoneBook: MutableList<PhoneEntry>) {
    println("Start searching (quick sort + binary search)...")

    val sortDuration = measure {
        phoneBook.quickSort()
    }

    var found = 0
    val searchDuration = measure {
        for (name in searchNames) {
            if (phoneBook.binarySearch(name) != null) {
                found++
            }
        }
    }

    println(
        "Found %d / %d entries. Time taken: %s"
            .format(found, searchNames.size, (sortDuration + searchDuration).toTimeString())
    )

    println("Sorting time: %s".format(sortDuration.toTimeString()))
    println("Searching time: %s".format(searchDuration.toTimeString()))
}

fun hashTableSearch(searchNames: MutableList<String>, phoneBook: MutableList<PhoneEntry>) {
    println("Start searching (hash table)...")
    var phoneMap = mutableMapOf<String, String>()
    val buildDuration = measure {
        phoneBook.map { phoneMap[it.name] = it.phone }
    }

    var found = 0
    val searchDuration = measure {
        for (name in searchNames) {
            if (phoneMap[name] != null) {
                found++
            }
        }
    }

    println(
        "Found %d / %d entries. Time taken: %s"
            .format(found, searchNames.size, (buildDuration + searchDuration).toTimeString())
    )

    println("Creating time: %s".format(buildDuration.toTimeString()))
    println("Searching time: %s".format(searchDuration.toTimeString()))
}

fun MutableList<PhoneEntry>.bubbleSort(limit: Duration) {
    val start = System.currentTimeMillis()

    var n = size
    do {
        var swapped = false
        for (i in 1 until n) {
            if (this[i - 1].name > this[i].name) {
                this[i] = this[i - 1].also { this[i - 1] = this[i] }
                swapped = true
            }
        }
        if (System.currentTimeMillis() - start > limit.toMillis()) {
            throw TimeoutException("Time limit reached")
        }
        n--
    } while (swapped)
}

fun MutableList<PhoneEntry>.quickSort() = this.quickSort(0, size - 1)

fun MutableList<PhoneEntry>.quickSort(lo: Int, hi: Int) {
    if (lo >= hi) return
    val mid = partition(lo, hi)
    this.quickSort(lo, mid - 1);
    this.quickSort(mid + 1, hi);
}

fun MutableList<PhoneEntry>.partition(lo: Int, hi: Int): Int {
    var i = lo
    var j = hi + 1
    val pivot = this[lo]

    while (true) {
        while (i < hi && this[++i].name <= pivot.name);
        while (j > lo && this[--j].name >= pivot.name);

        if (i >= j) break

        this[i] = this[j].also { this[j] = this[i] }
    }

    this[lo] = this[j].also { this[j] = this[lo] }
    return j
}

fun MutableList<PhoneEntry>.clone(): MutableList<PhoneEntry> {
    return this.map { it.copy() }.toMutableList()
}

fun MutableList<PhoneEntry>.linearSearch(name: String): PhoneEntry? {
    if (isEmpty()) {
        return null
    }

    for (entry in this) {
        if (entry.name == name) {
            return entry
        }
    }

    return null
}

fun MutableList<PhoneEntry>.jumpSearch(name: String): PhoneEntry? {
    if (isEmpty()) {
        return null
    }

    var curr = 0
    var prev = 0
    var last = size - 1
    val step = sqrt(1.0 * size).toInt()

    while (this[curr].name < name) {
        if (curr == last) {
            return null
        }
        prev = curr
        curr = min(curr + step, last)
    }

    while (this[curr].name > name) {
        curr--
        if (curr <= prev) {
            return null
        }
    }

    if (this[curr].name == name) {
        return this[curr]
    }

    return null
}

fun MutableList<PhoneEntry>.binarySearch(name: String): PhoneEntry? {
    if (isEmpty()) {
        return null
    }

    var lo = 0
    var hi = size - 1
    while (lo <= hi) {
        val mid = lo + (hi - lo) / 2
        val entry = this[mid]
        if (entry.name == name) {
            return entry
        }

        if (entry.name < name) {
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }

    return null
}

fun measure(procedure: () -> Unit): Duration {
    val startTime = System.currentTimeMillis()
    procedure()
    val endTime = System.currentTimeMillis()
    return Duration.ofMillis(endTime - startTime)
}

fun Duration.toTimeString() = "%d min. %d sec. %d ms.".format(toMinutesPart(), toSecondsPart(), toMillisPart())
