package gitinternals

import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.zip.InflaterInputStream

data class TreeEntry(val mode: Int, val filename: String, val hash: String) {
    override fun toString(): String {
        return "%d %s %s".format(mode, hash, filename)
    }
}

data class Contribution(
    val authorName: String,
    val authorEmail: String,
    val date: ZonedDateTime,
    val isOriginal: Boolean) {

    companion object {
        fun read(s: String, isOriginal: Boolean): Contribution {
            val pattern = "^(?<name>.+) <(?<email>.+)> (?<epoch>\\d+) (?<zone>[+-]\\d{2}\\d{2})$".toRegex()
            val match = pattern.find(s) ?: throw RuntimeException("Can't read contribution from %s".format(s))

            val (name, email, epoch, zone) = match.destructured
            val instant = Instant.ofEpochSecond(epoch.toLong())

            val zoneId = ZoneId.of(zone)
            val zonedDateTime = ZonedDateTime.ofInstant(instant, zoneId)

            return Contribution(name, email, zonedDateTime, isOriginal)
        }
    }

    override fun toString(): String {
        val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss xxx")
        return "%s %s %s timestamp: %s".format(
            authorName,
            authorEmail,
            if (isOriginal) "original" else "commit",
            date.format(formatter)
        )
    }
}

enum class GitObjectType(val typeName: String) {
    BLOB("blob"),
    TREE("tree"),
    COMMIT("commit");

    companion object {
        fun fromName(name: String): GitObjectType {
            for (type in GitObjectType.values()) {
                if (name == type.typeName) {
                    return type
                }
            }

            throw RuntimeException("Unknown git object type %s".format(name))
        }
    }
}

data class LogCommit(val commitObj: GitCommitObject, val isMerged: Boolean = false) {
    fun print() {
        println("Commit: ${commitObj.hash}${if (isMerged) " (merged)" else ""}")
        println(commitObj.committerContrib)
        println(commitObj.message)
    }
}

class GitCommitObject internal constructor(hash: String, length: Int) : GitObject(hash, length) {
    lateinit var tree: String private set
    lateinit var mergeParent: String private set
    lateinit var parent: String private set
    lateinit var authorContrib: Contribution private set
    lateinit var committerContrib: Contribution private set
    lateinit var message: String private set

    override fun readContent(iis: InflaterInputStream) {
        val parents = LinkedList<String>()

        // read commit properties
        while (iis.available() != 0) {
            val line = iis.readLine()
            if (line.isEmpty()) {
                break
            }

            val spaceIndex = line.indexOf(" ")
            val propName = line.substring(0 until spaceIndex)
            val propValue = line.substring(spaceIndex + 1)

            when (propName) {
                "tree" -> tree = propValue
                "parent" -> parents.addLast(propValue)
                "author" -> authorContrib = Contribution.read(propValue, true)
                "committer" -> committerContrib = Contribution.read(propValue, false)
            }
        }

        parent = if (parents.isNotEmpty()) parents.pollFirst() else ""
        mergeParent = if (parents.isNotEmpty()) parents.pollFirst() else ""
        message = iis.readToEnd()
    }

    override fun printContent() {
        println("*COMMIT*")
        println("tree: $tree")
        if (mergeParent.isNotEmpty() && parent.isNotEmpty()) {
            println("parents: $parent | $mergeParent")
        } else if (parent.isNotEmpty()) {
            println("parents: $parent")
        }

        println("author: $authorContrib")
        println("committer: $committerContrib")

        println("commit message:")
        println(message)
    }
}

class GitTreeObject internal constructor(hash: String, length: Int) : GitObject(hash, length) {
    val entries = mutableListOf<TreeEntry>()

    override fun readContent(iis: InflaterInputStream) {
        while (iis.available() != 0) {
            val mode = iis.readToSpace().toInt()
            val filename = iis.readToNull()
            val hash = byteArrayToHex(iis.readNBytes(20))

            entries.add(TreeEntry(mode, filename, hash))
        }
    }

    override fun printContent() {
        println("*TREE*")
        for (entry in entries) {
            println(entry)
        }
    }
}

class GitBlobObject internal constructor(hash: String, length: Int) : GitObject(hash, length) {
    private lateinit var content: String
    override fun readContent(iis: InflaterInputStream) {
        content = iis.readToEnd()
    }

    override fun printContent() {
        println("*BLOB*")
        println(content)
    }
}

class GitBranch(private val gitRepository: GitRepository, private val branchName: String) {
    fun getCommits(): List<LogCommit> {
        val path = gitRepository.getBranchesPath().resolve(branchName)
        val branchFile = path.toFile()
        val commitHash = branchFile.readText().trim()

        val commits = mutableListOf<LogCommit>()

        var commitObj = gitRepository.getObject(commitHash) as GitCommitObject
        commits.add(LogCommit(commitObj, false))
        while (commitObj.parent.isNotEmpty()) {
            if (commitObj.mergeParent.isNotEmpty()) {
                val parentCommitObj = gitRepository.getObject(commitObj.mergeParent) as GitCommitObject
                commits.add(LogCommit(parentCommitObj, true))
            }

            commitObj = gitRepository.getObject(commitObj.parent) as GitCommitObject
            commits.add(LogCommit(commitObj, false))
        }

        return commits
    }
}

abstract class GitObject internal constructor(
    val hash: String,
    val length: Int) {
    companion object {
        fun read(gitRepo: GitRepository, hash: String): GitObject {
            val path = gitRepo.path
            val objDir = hash.substring(0, 2)
            val objFile = hash.substring(2)

            val gitObjPath = Path.of(path.toString(), "objects", objDir, objFile)

            FileInputStream(gitObjPath.toFile()).use { fis ->
                val iis = InflaterInputStream(fis)
                val header = iis.readToNull()

                val (typeName, len) = header.split(" ")
                val length = len.toInt()

                val gitObj = when (GitObjectType.fromName(typeName)) {
                    GitObjectType.BLOB -> GitBlobObject(hash, length)
                    GitObjectType.TREE -> GitTreeObject(hash, length)
                    GitObjectType.COMMIT -> GitCommitObject(hash, length)
                }

                gitObj.readContent(iis)
                return gitObj
            }
        }
    }

    protected abstract fun readContent(iis: InflaterInputStream)
    abstract fun printContent()
}

class GitRepository(val path: Path) {
    fun getObject(hash: String): GitObject {
        return GitObject.read(this, hash)
    }

    fun getBranch(branchName: String): GitBranch {
        return GitBranch(this, branchName)
    }

    fun getBranches(): List<String> {
        val branchFiles = getBranchesPath().toFile()
            .listFiles { file -> file.isFile }?.toList() ?: emptyList()

        return branchFiles.map { it.name }.sorted()
    }

    fun getCurrentBranch(): String {
        val pattern = "^ref: refs/heads/(?<branch>.+)$".toRegex()
        val headFile = Path.of(path.toString(), "HEAD").toFile()
        val content = headFile.readText()
        val match = pattern.find(content) ?: throw RuntimeException("Can't read current branch from %s".format(headFile.path))

        return match.groups["branch"]?.value ?: ""
    }

    fun getCommitTree(commitHash: String): List<String> {
        val commitObj = getObject(commitHash) as GitCommitObject
        val treeObj = getObject(commitObj.tree) as GitTreeObject

        val files = mutableListOf<String>()
        collectFiles(treeObj, "", files)

        return files
    }

    fun getBranchesPath(): Path = Path.of(path.toString(), "refs", "heads")

    private fun collectFiles(treeObj: GitTreeObject, currentPath: String, files: MutableList<String>) {
        for (entry in treeObj.entries) {
            val path = if (currentPath.isNotEmpty()) "$currentPath/${entry.filename}" else entry.filename

            val gitObj = getObject(entry.hash)
            if (gitObj is GitTreeObject) {
                collectFiles(gitObj, path, files)
            } else {
                files.add(path)
            }
        }
    }
}

fun InflaterInputStream.readToNull() = readUntil(0)
fun InflaterInputStream.readLine() = readUntil('\n'.code)
fun InflaterInputStream.readToSpace() = readUntil(' '.code)

fun InflaterInputStream.readUntil(terminateByte: Int): String {
    val sb: StringBuilder = StringBuilder()
    while (available() != 0) {
        when (val byte = read()) {
            terminateByte -> break
            else -> sb.append(byte.toChar())
        }
    }
    return sb.toString()
}

fun InflaterInputStream.readToEnd(): String {
    val result = ByteArrayOutputStream()
    val buffer = ByteArray(1024)
    var length: Int
    while (read(buffer).also { length = it } != -1) {
        result.write(buffer, 0, length)
    }

    return result.toString()
}

fun byteArrayToHex(bytes: ByteArray): String {
    val sb = StringBuilder(bytes.size * 2)
    for (byte in bytes) {
        sb.append(String.format("%02x", byte))
    }
    return sb.toString()
}

fun main() {
    println("Enter .git directory location:")
    val gitPath = Path.of(readln())
    val gitRepository = GitRepository(gitPath)

    println("Enter command:")
    when (readln()) {
        "cat-file" -> catFile(gitRepository)
        "list-branches" -> listBranches(gitRepository)
        "log" -> logBranch(gitRepository)
        "commit-tree" -> commitTree(gitRepository)
    }
}

private fun catFile(gitRepository: GitRepository) {
    println("Enter git object hash:")
    val gitHash = readln()
    val gitObj = gitRepository.getObject(gitHash)
    gitObj.printContent()
}

private fun listBranches(gitRepository: GitRepository) {
    val branches = gitRepository.getBranches()
    val currBranch = gitRepository.getCurrentBranch()
    for (branch in branches) {
        println("%s %s".format(if (branch == currBranch) "*" else " ", branch))
    }
}

private fun logBranch(gitRepository: GitRepository) {
    println("Enter branch name:")
    val branchName = readln()
    val branch = gitRepository.getBranch(branchName)
    val commits = branch.getCommits()

    for (commit in commits) {
        commit.print()
    }
}

private fun commitTree(gitRepository: GitRepository) {
    println("Enter commit-hash:")
    val commitHash = readln()
    val files = gitRepository.getCommitTree(commitHash)
    for (file in files) {
        println(file)
    }
}