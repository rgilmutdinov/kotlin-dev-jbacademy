package chess

import kotlin.math.abs

const val CMD_EXIT = "exit"
const val CMD_MOVE = """^[a-h][1-8][a-h][1-8]$"""

const val BLANK_SQUARE = ' '
const val BOARD_SIZE = 8

const val ERR_INVALID_INPUT = "Invalid Input"
const val ERR_NO_PAWN = "No %s pawn at %s"

const val INIT_RANK_WHITE = 2
const val INIT_RANK_BLACK = 7
const val WIN_RANK_WHITE = 8
const val WIN_RANK_BLACK = 1

enum class Command(pattern: String = "") {
    EXIT(CMD_EXIT),
    MOVE(CMD_MOVE),
    UNKNOWN;

    val regex = pattern.toRegex()

    companion object {
        fun fromString(s: String): Command {
            values().forEach {
                if (it != UNKNOWN && s.matches(it.regex)) {
                    return it
                }
            }

            return UNKNOWN
        }
    }
}

data class Position(val file: Char, val rank: Int) {

    companion object {
        fun parse(s: String, fromIndex: Int = 0): Position {
            val file = s[fromIndex]
            val rank = s[fromIndex + 1] - '0'
            return Position(file, rank)
        }
    }

    fun neighbor(deltaRank: Int, deltaFile: Int): Position {
        var (row, col) = toIndices()
        row += deltaRank
        col += deltaFile
        return Position('a' + col, row + 1)
    }

    fun notation(): String = "$file$rank"
    fun toIndices(): List<Int> {
        return listOf(rank - 1, file - 'a')
    }

    override fun toString(): String = notation()

    fun verticalDistTo(pos: Position): Int {
        return abs(toIndices()[0] - pos.toIndices()[0])
    }
}

enum class MoveType {
    MOVE,
    CAPTURE,
    CAPTURE_EN_PASSANT
}

data class Move(val pawn: Pawn, val to: Position, val type: MoveType, val capturedPawn: Pawn? = null) {
    fun execute() {
        capturedPawn?.remove()
        pawn.move(to)
    }

    fun isWinMove(): Boolean {
        return to.rank == pawn.player.getWinRank()
    }
}

enum class Color(val direction: Int, val initRank: Int, val winRank: Int) {
    WHITE(+1, INIT_RANK_WHITE, WIN_RANK_WHITE),
    BLACK(-1, INIT_RANK_BLACK, WIN_RANK_BLACK);

    fun toChar(): Char = name.first()
    override fun toString(): String {
        return name.replaceFirstChar(Char::titlecase)
    }
}

enum class GameStatus {
    IN_PROGRESS,
    WIN,
    STALEMATE
}

class Chess(private val playerName1: String, private val playerName2: String) {
    private val player1 = Player(playerName1, Color.WHITE)
    private val player2 = Player(playerName2, Color.BLACK)

    private var currentPlayer = player1
    private var status: GameStatus = GameStatus.IN_PROGRESS

    private var lastMove: Pair<Position, Position>? = null

    fun makeMove(input: String) {
        if (isEnded()) {
            return
        }

        val fromPos: Position = Position.parse(input, 0)
        val toPos: Position = Position.parse(input, 2)

        if (!currentPlayer.isPawnAt(fromPos)) {
            val error = String.format(ERR_NO_PAWN, currentPlayer.color, fromPos.notation())
            throw IllegalArgumentException(error)
        }

        val pawn: Pawn = currentPlayer.pawnAt(fromPos)
        val moves: List<Move> = pawn.availableMoves(this)

        val move: Move = moves.firstOrNull { it.to == toPos } ?: throw IllegalArgumentException(ERR_INVALID_INPUT)

        move.execute()
        lastMove = Pair(fromPos, toPos)

        if (move.isWinMove()) {
            status = GameStatus.WIN
            return
        }

        val oppPlayer: Player = oppositePlayer()
        if (!oppPlayer.hasPawns()) {
            status = GameStatus.WIN
            return
        }

        if (!oppPlayer.hasMoves(this)) {
            status = GameStatus.STALEMATE
            return
        }

        currentPlayer = oppPlayer
    }

    fun inBounds(position: Position): Boolean {
        val (file, rank) = position
        return file in 'a'..'a' + BOARD_SIZE && rank in 1..BOARD_SIZE
    }

    fun isPositionEmpty(position: Position): Boolean {
        if (!inBounds(position)) {
            return false
        }
        return !player1.isPawnAt(position) && !player2.isPawnAt(position)
    }

    fun getCurrentPlayerName(): String = if (currentPlayer == player1) playerName1 else playerName2

    fun printBoard() {
        val board = Array(BOARD_SIZE) { CharArray(BOARD_SIZE) { BLANK_SQUARE } }

        player1.renderPawns(board)
        player2.renderPawns(board)

        printLine()
        for (rank in BOARD_SIZE - 1 downTo 0) {
            printRank(board, rank)
            printLine()
        }
        printFiles()
    }

    fun getLastMove(): Pair<Position, Position>? = lastMove

    fun isEnded() = status != GameStatus.IN_PROGRESS
    fun printStatus() {
        println(
            when (status) {
                GameStatus.WIN -> "${currentPlayer.color} Wins!"
                GameStatus.STALEMATE -> "Stalemate!"
                else -> "In progress..."
            }
        )
    }

    fun oppositePlayerTo(player: Player): Player = if (player == player1) player2 else player1
    private fun oppositePlayer(): Player = oppositePlayerTo(currentPlayer)

    private fun printLine() {
        println("  +${"---+".repeat(8)}")
    }

    private fun printRank(board: Array<CharArray>, rank: Int) {
        println("${rank + 1} |${board[rank].joinToString("|") { " $it " }}|")
    }

    private fun printFiles() {
        println("    ${Array('h' - 'a' + 1) { i -> 'a' + i }.joinToString(separator = "   ")}")
    }
}

class Pawn(val player: Player, var position: Position) {
    private var movesCount = 0

    fun move(to: Position) {
        position = to
        movesCount++
    }

    fun remove() {
        player.removePawn(this)
    }

    fun hasMoves(chess: Chess) = availableMoves(chess).isNotEmpty()

    fun availableMoves(chess: Chess): List<Move> {
        val moves = mutableListOf<Move>()
        val color = player.color

        // move forward positions
        val frontPos = Position(position.file, position.rank + color.direction)
        if (chess.isPositionEmpty(frontPos)) {
            moves.add(Move(this, frontPos, MoveType.MOVE))

            if (movesCount == 0) {
                val frontPos2 = Position(position.file, position.rank + 2 * color.direction)
                if (chess.isPositionEmpty(frontPos2)) {
                    moves.add(Move(this, frontPos2, MoveType.MOVE))
                }
            }
        }

        val capturePositions = mutableListOf(
            position.neighbor(color.direction, -1),
            position.neighbor(color.direction, +1)
        )

        for (capturePosition in capturePositions) {
            if (!chess.inBounds(capturePosition)) {
                continue
            }

            val oppositePlayer: Player = chess.oppositePlayerTo(player)
            if (oppositePlayer.isPawnAt(capturePosition)) {
                val targetPawn = oppositePlayer.pawnAt(capturePosition)
                val captureMove = Move(this, capturePosition, MoveType.CAPTURE, targetPawn)
                moves.add(captureMove)
            } else {
                // check en passant positions
                val lastPos: Pair<Position, Position> = chess.getLastMove() ?: continue

                val pawnPosition = capturePosition.neighbor(-color.direction, 0)
                if (!chess.inBounds(pawnPosition)) {
                    continue
                }

                if (oppositePlayer.isPawnAt(pawnPosition)) {
                    val targetPawn: Pawn = oppositePlayer.pawnAt(pawnPosition)
                    if (targetPawn.movesCount == 1 &&
                        lastPos.second == targetPawn.position &&
                        lastPos.second.verticalDistTo(lastPos.first) == 2) {
                        val captureMove = Move(this, capturePosition, MoveType.CAPTURE_EN_PASSANT, targetPawn)
                        moves.add(captureMove)
                    }
                }
            }
        }

        return moves
    }
}

data class Player(val playerName: String, val color: Color) {
    private val pawns = ('a'..'h').map { Pawn(this, Position(it, color.initRank)) }.toMutableList()

    fun isPawnAt(position: Position) = pawns.any { it.position == position }
    fun pawnAt(position: Position) = pawns.first { it.position == position }
    fun removePawn(pawn: Pawn) = pawns.remove(pawn)

    fun hasMoves(chess: Chess) = pawns.any { it.hasMoves(chess) }
    fun hasPawns() = pawns.isNotEmpty()

    fun getWinRank() = color.winRank

    fun renderPawns(board: Array<CharArray>) {
        for (pawn in pawns) {
            val (row, col) = pawn.position.toIndices()
            board[row][col] = color.toChar()
        }
    }
}

fun main() {
    printGameName()

    val player1 = readName("First Player's name:")
    val player2 = readName("Second Player's name:")
    val game = Chess(player1, player2)

    game.printBoard()
    while (!game.isEnded()) {
        while (true) {
            val input = prompt(game.getCurrentPlayerName())
            when (Command.fromString(input)) {
                Command.EXIT -> {
                    endGame()
                    return
                }
                Command.MOVE -> {
                    try {
                        game.makeMove(input)
                        break
                    } catch (e: IllegalArgumentException) {
                        println(e.message)
                    }
                    continue
                }
                else -> continue
            }
        }
        game.printBoard()
    }

    game.printStatus()
    endGame()
}

fun endGame() {
    println("Bye!")
}

fun prompt(player: String): String {
    println("$player's turn:")
    return readln()
}

fun readName(prompt: String): String {
    println(prompt)
    return readln()
}

fun printGameName() {
    println(" Pawns-Only Chess")
}