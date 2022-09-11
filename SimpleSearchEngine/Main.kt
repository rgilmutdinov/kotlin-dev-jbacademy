package search

import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val dataArgIndex = args.indexOf("--data")
    if (dataArgIndex == -1 || dataArgIndex + 1 >= args.size) {
        throw IllegalArgumentException("Data file is not specified.")
    }

    val filePath = args[dataArgIndex + 1]
    val dictionary = PeopleDictionary()
    dictionary.readFromFile(filePath)

    val cmdFactory = CommandFactory()
    while (true) {
        val command = cmdFactory.promptCommand()
        if (command == Command.INVALID) {
            println("Incorrect option! Try again.")
            continue
        }
        command.execute(dictionary)
    }
}

enum class SearchStrategy {
    ALL,
    ANY,
    NONE
}

class PeopleDictionary {
    private val splitRegex = "\\s+".toRegex()

    private val people: MutableList<String> = mutableListOf()
    private val invertedIndex: MutableMap<String, MutableSet<Int>> = mutableMapOf()

    fun search(query: String, strategy: SearchStrategy): List<String> {
        val words = query.lowercase().split(splitRegex)

        val indices = when (strategy) {
            SearchStrategy.ALL -> searchAll(words)
            SearchStrategy.ANY -> searchAny(words)
            SearchStrategy.NONE -> searchNone(words)
        }

        return indices.map { people[it] }
    }

    fun readFromFile(filePath: String) {
        var id = 0
        File(filePath).forEachLine {
            val tokens = it.split(splitRegex)
            for (token in tokens) {
                invertedIndex
                    .computeIfAbsent(token.lowercase()) { mutableSetOf() }
                    .add(id)
            }
            id++
            people.add(it)
        }
    }

    fun print() {
        println("=== List of people ===")
        for (person in people) {
            println(person)
        }
    }

    private fun searchNone(words: List<String>): Set<Int> {
        val indices = mutableSetOf<Int>()
        for (i in 0 until people.size) {
            indices.add(i)
        }

        for (word in words) {
            indices -= getWordIndices(word)
            if (indices.isEmpty()) {
                break
            }
        }

        return indices
    }

    private fun searchAny(words: List<String>): Set<Int> {
        val indices = mutableSetOf<Int>()
        for (word in words) {
            indices += getWordIndices(word)
        }
        return indices
    }

    private fun searchAll(words: List<String>): Set<Int> {
        if (words.isEmpty()) {
            return emptySet()
        }

        val indices = getWordIndices(words.first())
        for (i in 1..words.lastIndex) {
            if (indices.isEmpty()) {
                return emptySet()
            }

            indices -= getWordIndices(words[i])
        }
        return indices
    }

    private fun getWordIndices(word: String): MutableSet<Int> =
        invertedIndex[word] ?: mutableSetOf()
}

class CommandFactory {
    fun promptCommand(): Command {
        printMenu()
        return Command.fromString(readln())
    }

    private fun printMenu() {
        println("=== Menu ===")
        Command.values().filter { it != Command.INVALID }.forEach { println(it) }
    }
}

enum class Command(private val id: Int, private val itemName: String) {
    SEARCH(1, "Search information") {
        override fun execute(dictionary: PeopleDictionary) {
            val searchStrategy = promptSearchStrategy()

            println("Enter a name or email to search all matching people.")
            val found = dictionary.search(readln(), searchStrategy)
            if (found.isEmpty()) {
                println("No matching people found.")
            } else {
                println("${found.size} person${ if (found.size > 1) "s" else "" } found:")
                for (person in found) {
                    println(person)
                }
            }
        }

        private fun promptSearchStrategy(): SearchStrategy {
            while (true) {
                println("Select a matching strategy: ${SearchStrategy.values().joinToString { it.name }}")
                val input = readln()
                for (strategy in SearchStrategy.values()) {
                    if (strategy.name == input.uppercase()) {
                        return strategy
                    }
                }
            }
        }
    },
    PRINT(2, "Print all data") {
        override fun execute(dictionary: PeopleDictionary) = dictionary.print()
    },
    EXIT(0, "Exit") {
        override fun execute(dictionary: PeopleDictionary) {
            println("Bye!")
            exitProcess(0)
        }
    },
    INVALID(-1, "");

    companion object {
        fun fromString(input: String): Command {
            val number = input.toIntOrNull() ?: return INVALID

            for (command in Command.values()) {
                if (command != INVALID && command.id == number) {
                    return command
                }
            }

            return INVALID
        }
    }

    override fun toString(): String {
        return "$id. $itemName"
    }

    open fun execute(dictionary: PeopleDictionary) {}
}