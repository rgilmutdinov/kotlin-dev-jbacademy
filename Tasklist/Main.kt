package tasklist

import kotlinx.datetime.*
import com.squareup.moshi.*
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File

class LocalDateTimeJsonAdapter {
    @ToJson
    fun toJson(localDateTime: LocalDateTime): String {
        return localDateTime.toString()
    }

    @FromJson
    fun fromJson(json: String): LocalDateTime {
        return LocalDateTime.parse(json)
    }
}

enum class Color(val code: String) {
    RED("\u001B[101m"),
    YELLOW("\u001B[103m"),
    GREEN("\u001B[102m"),
    BLUE("\u001B[104m"),
    BLACK("\u001B[0m");
}

enum class Priority(val color: Color) {
    CRITICAL(Color.RED),
    HIGH(Color.YELLOW),
    NORMAL(Color.GREEN),
    LOW(Color.BLUE);

    companion object {
        fun prompt(): Priority {
            while (true) {
                println("Input the task priority (${symbols()}):")
                val input = readln()
                val priority = values().firstOrNull { it.toSymbol() == input.uppercase() }
                if (priority != null) {
                    return priority
                }
            }
        }
        private fun symbols(): String {
            return values().joinToString(", ") { it.toSymbol() }
        }
    }

    fun toSymbol(): String = "${name[0]}"
}

enum class DueTag(val color: Color) {
    IN_TIME(Color.GREEN),
    TODAY(Color.YELLOW),
    OVERDUE(Color.RED);

    fun toSymbol(): String = "${name[0]}"
}

class Task private constructor (
    var priority: Priority,
    var date: LocalDateTime,
    var text: List<String>) {

    companion object  {
        fun read(): Task {
            val priority = Priority.prompt()
            val date = promptDateTime()
            val lines = promptText()

            return Task(priority, date, lines)
        }

        private fun promptText(): MutableList<String> {
            val lines = mutableListOf<String>()
            println("Input a new task (enter a blank line to end):")
            while (true) {
                val line = readln().trim()
                if (line.isEmpty()) {
                    break
                }

                lines += line
            }
            return lines
        }

        private fun promptDate(): LocalDate {
            val regex = """^(?<year>\d{1,4})-(?<month>\d{1,2})-(?<day>\d{1,2})$""".toRegex()
            while (true) {
                println("Input the date (yyyy-mm-dd):")
                val input = readln()
                try {
                    val match = regex.matchEntire(input)
                    if (match != null) {
                        val (year, month, day) = match.destructured.toList().map { it.toInt() }
                        return LocalDate(year, month, day)
                    }
                } catch (e: Exception) {
                    // just continue the loop
                }

                println("The input date is invalid")
            }
        }

        private fun promptTimeForDate(date: LocalDate): LocalDateTime {
            val regex = """^(?<hours>\d{1,2}):(?<minutes>\d{1,2})$""".toRegex()
            while (true) {
                println("Input the time (hh:mm):")
                val input = readln()
                try {
                    val match = regex.matchEntire(input)
                    if (match != null) {
                        val (hours, minutes) = match.destructured.toList().map { it.toInt() }
                        return date.atTime(hours, minutes)
                    }
                } catch (e: Exception) {
                    // just continue the loop
                }
                println("The input time is invalid")
            }
        }

        private fun promptDateTime(): LocalDateTime = promptTimeForDate(promptDate())
    }

    fun readPriority() {
        priority = Priority.prompt()
    }

    fun readDate() {
        date = promptDate().atTime(date.hour, date.minute)
    }

    fun readTime() {
        date = promptTimeForDate(date.toLocalDate())
    }

    fun readText() {
        text = promptText()
    }

    fun isEmpty() = text.isEmpty()

    fun dueTo(toDate: LocalDate): DueTag {
        val days = toDate.daysUntil(date.toLocalDate())
        return when {
            days == 0 -> DueTag.TODAY
            days >  0 -> DueTag.IN_TIME
            else -> DueTag.OVERDUE
        }
    }
}

enum class AlignmentType {
    LEFT,
    MIDDLE,
    OFFSET;
}

data class Alignment(val alignmentType: AlignmentType, val offset: Int = 0)

data class Cell(
    val paragraphs: List<String>,
    val color: Color? = null,
    val align: Alignment? = null) {
    constructor(s: String, color: Color? = null, align: Alignment? = null)
            : this(listOf(s), color, align)
}

data class TableColumn(
    val colName: String,
    val width: Int,
    val contentAlign: Alignment,
    val headerAlign: Alignment = Alignment(AlignmentType.MIDDLE))

class Table {
    private val columns = mutableListOf<TableColumn>()

    fun addColumn(col: TableColumn) {
        columns.add(col)
    }

    fun print(rows: List<List<Cell>>) {
        printHeader()
        for (row in rows) {
            printRow(row)
            printHorizontalLine()
        }
    }

    private fun printHeader() {
        printHorizontalLine()
        printRow(columns.map { Cell(it.colName, align = it.headerAlign ) })
        printHorizontalLine()
    }

    private fun printHorizontalLine() {
        val cells = mutableListOf<String>()
        for (col in columns) {
            cells.add("-".repeat(col.width))
        }
        println("+${cells.joinToString("+")}+")
    }

    private fun printRow(row: List<Cell>) {
        val lines = row.mapIndexed { i, c -> getLines(c, columns[i]) }
        val maxHeight = lines.maxOfOrNull { it.size } ?: 0
        for (h in 1..maxHeight) {
            val output = mutableListOf<String>()
            for ((index, line) in lines.withIndex()) {
                val col = columns[index]
                if (line.size < h) {
                    output.add(" ".repeat(col.width))
                } else {
                    output.add(line[h - 1])
                }
            }

            println("|${output.joinToString("|")}|")
        }
    }

    private fun getLines(cell: Cell, col: TableColumn): List<String> {
        val lines = mutableListOf<String>()
        for (paragraph in cell.paragraphs) {
            val chunks = paragraph.chunked(col.width)
            for (i in 0 until chunks.size - 1) {
                lines.add(getColoredText(chunks[i], cell.color))
            }

            val lastChunk = chunks.last()
            val pad = col.width - lastChunk.length
            val align = cell.align ?: col.contentAlign
            when (val alignType = align.alignmentType) {
                AlignmentType.LEFT -> {
                    lines.add("%s%s".format(
                        getColoredText(lastChunk, cell.color),
                        " ".repeat(pad)))
                }
                AlignmentType.MIDDLE, AlignmentType.OFFSET -> {
                    val padL = if (alignType == AlignmentType.MIDDLE) pad / 2 else align.offset
                    val padR = pad - padL

                    lines.add("%s%s%s".format(
                        " ".repeat(padL),
                        getColoredText(lastChunk, cell.color),
                        " ".repeat(padR)))
                }
            }
        }
        return lines
    }

    private fun getColoredText(text: String, color: Color?) =
        if (color != null) "${color.code}${text}${Color.BLACK.code}" else text
}

class TaskManager {
    private val tasks = mutableListOf<Task>()

    fun loadFrom(file: File): Boolean {
        val taskListAdapter = createTaskAdapter()

        val json = file.readText()
        val fileTasks = taskListAdapter.fromJson(json) ?: return false

        tasks.clear()
        return tasks.addAll(fileTasks.filterNotNull())
    }

    private fun createTaskAdapter(): JsonAdapter<List<Task?>> {
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .add(LocalDateTimeJsonAdapter())
            .build()

        val type = Types.newParameterizedType(List::class.java, Task::class.java)
        return moshi.adapter(type)
    }

    fun saveTo(file: File) {
        val taskListAdapter = createTaskAdapter()

        val json = taskListAdapter.toJson(tasks)
        file.writeText(json)
    }

    fun addTask(task: Task) = tasks.add(task)
    fun hasTasks() = tasks.isNotEmpty()
    fun countTasks() = tasks.size

    fun prettyPrint() {
        if (tasks.isEmpty()) {
            println("No tasks have been input")
            return
        }

        val table = Table()
        table.addColumn(TableColumn("N", 4, Alignment(AlignmentType.MIDDLE)))
        table.addColumn(TableColumn("Date", 12, Alignment(AlignmentType.MIDDLE)))
        table.addColumn(TableColumn("Time", 7, Alignment(AlignmentType.MIDDLE)))
        table.addColumn(TableColumn("P", 3, Alignment(AlignmentType.MIDDLE)))
        table.addColumn(TableColumn("D", 3, Alignment(AlignmentType.MIDDLE)))

        // hack: "Task" column is not aligned exactly in the middle
        // handle this header with special alignment AlignmentType.OFFSET
        table.addColumn(TableColumn("Task", 44, Alignment(AlignmentType.LEFT), Alignment(AlignmentType.OFFSET, 19)))

        val currentDate = Clock.System.now().toLocalDateTime(TimeZone.of("UTC+0")).date
        val rows: MutableList<List<Cell>> = mutableListOf()
        for ((index, task) in tasks.withIndex()) {
            val rowNumber = index + 1
            val row = mutableListOf(Cell(rowNumber.toString()))
            row += taskAsList(task, currentDate)
            rows.add(row)
        }

        table.print(rows)
    }

    fun getTask(index: Int): Task = tasks[index]
    fun removeTask(index: Int): Task = tasks.removeAt(index)

    private fun taskAsList(task: Task, currentDate: LocalDate): List<Cell> {
        with (task) {
            val list = mutableListOf<Cell>()
            list.add(Cell(date.toLocalDate().toString()))
            list.add(Cell("%02d:%02d".format(date.hour, date.minute)))
            list.add(Cell(" ", priority.color))
            list.add(Cell(" ", task.dueTo(currentDate).color))
            list.add(Cell(text))
            return list
        }
    }
}

fun main() {
    val taskManager = TaskManager()
    val jsonFile = File("tasklist.json")
    if (jsonFile.exists()) {
        taskManager.loadFrom(jsonFile)
    }

    while (true) {
        println("Input an action (add, print, edit, delete, end):")
        val action = readln().trim()
        when (action.lowercase()) {
            "add" -> addTask(taskManager)
            "edit" -> editTask(taskManager)
            "delete" -> deleteTask(taskManager)
            "print" -> printTasks(taskManager)
            "end" -> {
                println("Tasklist exiting!")
                break
            }
            else -> {
                println("The input action is invalid")
            }
        }
    }

    taskManager.saveTo(jsonFile)
}

private fun addTask(taskManager: TaskManager) {
    val task = Task.read()
    if (task.isEmpty()) {
        println("The task is blank")
        return
    }
    taskManager.addTask(task)
}

private fun deleteTask(taskManager: TaskManager) {
    taskManager.prettyPrint()
    if (!taskManager.hasTasks()) {
        return
    }

    val taskNumber = promptTaskNumber(taskManager)
    taskManager.removeTask(taskNumber - 1)
    println("The task is deleted")
}

private fun editTask(taskManager: TaskManager) {
    taskManager.prettyPrint()
    if (!taskManager.hasTasks()) {
        return
    }

    val taskNumber = promptTaskNumber(taskManager)
    val task = taskManager.getTask(taskNumber - 1)
    while (true) {
        println("Input a field to edit (priority, date, time, task):")
        when (readln()) {
            "priority" -> task.readPriority()
            "date" -> task.readDate()
            "time" -> task.readTime()
            "task" -> task.readText()
            else -> {
                println("Invalid field")
                continue
            }
        }
        break
    }
    println("The task is changed")
}

private fun printTasks(taskManager: TaskManager) {
    taskManager.prettyPrint()
}

fun LocalDateTime.toLocalDate() = LocalDate(year, month, dayOfMonth)

private fun promptTaskNumber(taskManager: TaskManager): Int {
    while (true) {
        println("Input the task number (1-${taskManager.countTasks()}):")
        val id = readln().toIntOrNull()
        if (id == null || id !in 1..taskManager.countTasks()) {
            println("Invalid task number")
            continue
        }

        return id
    }
}
