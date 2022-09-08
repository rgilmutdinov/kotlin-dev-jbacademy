package indigo

import kotlin.math.min

enum class Rank(val symbol: String, val score: Int = 0) {
    ACE("A", 1),
    TWO("2"),
    THREE("3"),
    FOUR("4"),
    FIVE("5"),
    SIX("6"),
    SEVEN("7"),
    EIGHT("8"),
    NINE("9"),
    TEN("10", 1),
    JACK("J", 1),
    QUEEN("Q", 1),
    KIND("K", 1);
}

enum class Suit(val symbol: String) {
    DIAMONDS("♦"),
    HEARTS("♥"),
    SPADES("♠"),
    CLUBS("♣");
}

data class Card(val rank: Rank, val suit: Suit) {
    fun isSameRankOrSuit(other: Card) = rank == other.rank || suit == other.suit

    override fun toString(): String {
        return "${rank.symbol}${suit.symbol}"
    }
}

open class Hand {
    protected val cards: MutableList<Card> = mutableListOf()

    fun clear() {
        cards.clear()
    }

    fun add(card: Card) {
        cards.add(card)
    }

    fun size() = cards.size
    fun isEmpty() = size() == 0
    fun first() = cards.first()
    fun random() = cards.random()

    fun removeAt(index: Int): Card? {
        if (index in 0 until size()) {
            return cards.removeAt(index)
        }

        return null
    }

    fun remove(card: Card) = cards.remove(card)

    fun giveLast(number: Int, otherHand: Hand): Int {
        var given = 0
        while (given < number && giveLast(otherHand)) {
            given++
        }

        return given
    }

    fun getMatchingCardsTo(card: Card): List<Card> {
        return cards.filter { it.isSameRankOrSuit(card) }.toList()
    }

    fun cardsBySuits(): Map<Suit, MutableList<Card>> {
        val map = HashMap<Suit, MutableList<Card>>()
        for (card in cards) {
            map.computeIfAbsent(card.suit) { mutableListOf() }.add(card)
        }
        return map
    }

    fun cardsByRanks(): Map<Rank, MutableList<Card>> {
        val map = HashMap<Rank, MutableList<Card>>()
        for (card in cards) {
            map.computeIfAbsent(card.rank) { mutableListOf() }.add(card)
        }
        return map
    }

    fun getScore(): Int {
        var score = 0
        for (card in cards) {
            score += card.rank.score
        }
        return score
    }

    fun giveAll(otherHand: Hand) {
        otherHand.cards.addAll(cards)
        cards.clear()
    }

    fun toLongString(): String {
        return "Cards in hand: " + cards.mapIndexed { i, it -> "${i + 1})$it"}.joinToString(" ")
    }

    private fun giveLast(otherHand: Hand): Boolean {
        return giveAt(size() - 1, otherHand)
    }

    private fun giveAt(index: Int, otherHand: Hand): Boolean {
        if (index in 0 until size()) {
            val card = cards.removeAt(index)
            otherHand.cards.add(card)
            return true
        }
        return false
    }

    override fun toString(): String {
        return cards.joinToString(" ")
    }
}

class Table : Hand() {
    fun top(): Card? = if (cards.isNotEmpty()) cards.last() else null

    fun toShortString(): String {
        if (isEmpty()) {
            return "No cards on the table"
        }
        return "${size()} cards on the table, and the top card is ${top()}"
    }

    override fun toString(): String {
        return cards.joinToString(" ")
    }
}

class Deck : Hand() {
    init {
        reset()
    }

    private fun populate(): MutableList<Card> {
        val cards = mutableListOf<Card>()
        for (suit in Suit.values()) {
            for (rank in Rank.values()) {
                val card = Card(rank, suit)
                cards.add(card)
            }
        }
        return cards
    }

    fun reset() {
        clear()
        cards.addAll(populate())
    }

    fun shuffle() = cards.shuffle()
}

abstract class Player {
    protected val hand: Hand = Hand()
    private val wonHand: Hand = Hand()

    fun reset() {
        hand.clear()
        wonHand.clear()
    }

    fun takeCards(number: Int, fromHand: Hand) {
        fromHand.giveLast(number, hand)
    }

    fun takeReward(fromHand: Hand) {
        fromHand.giveAll(wonHand)
    }

    fun hasCards(): Boolean = !hand.isEmpty()

    fun getScore(): Int = wonHand.getScore()
    fun wonCards(): Int = wonHand.size()

    abstract fun pickCard(topCard: Card?): Card
    abstract fun getName(): String
}

class GameTerminatedException : RuntimeException("Game terminated")

class Human : Player() {
    @Throws(GameTerminatedException::class)
    override fun pickCard(topCard: Card?): Card {
        if (hand.size() == 0) throw Exception("No cards.")

        println(hand.toLongString())
        while (true) {
            println("Choose a card to play (1-${hand.size()}):")
            val input = readln()
            if (input == "exit") {
                throw GameTerminatedException()
            }

            val cardNumber: Int? = input.toIntOrNull()
            if (cardNumber != null) {
                return hand.removeAt(cardNumber - 1) ?: continue
            }
        }
    }

    override fun getName(): String = "Player"
}

class Computer : Player() {
    override fun pickCard(topCard: Card?): Card {
        println(hand)

        val card = chooseCard(topCard)
        hand.remove(card)

        println("${getName()} plays $card")
        return card
    }

    private fun chooseCard(topCard: Card?): Card {
        // case 1
        if (hand.size() == 1) {
            return hand.first()
        }

        // case 3
        if (topCard == null) {
            return chooseRandomCard()
        }

        val candidates = hand.getMatchingCardsTo(topCard)

        // case 2
        if (candidates.size == 1) {
            return candidates.first()
        }

        // case 4
        if (candidates.isEmpty()) {
            return chooseRandomCard()
        }

        // case 5.1
        val suitCandidates = candidates.filter { it.suit == topCard.suit }.toList()
        if (suitCandidates.size >= 2) {
            return suitCandidates.random()
        }

        // case 5.2
        val rankCandidates = candidates.filter { it.rank == topCard.rank }.toList()
        if (rankCandidates.size >= 2) {
            return rankCandidates.random()
        }

        return candidates.random()
    }

    private fun chooseRandomCard(): Card {
        // get cards in hand with the same suit
        val suitCards = hand.cardsBySuits().filter { it.value.size > 1 }.flatMap { it.value }
        if (suitCards.isNotEmpty()) {
            return suitCards.random()
        }

        // get cards in hand with the same rank
        val rankCards = hand.cardsByRanks().filter { it.value.size > 1 }.flatMap { it.value }
        if (rankCards.isNotEmpty()) {
            return rankCards.random()
        }

        return hand.random()
    }

    override fun getName(): String = "Computer"
}

const val CARDS_ON_TABLE = 4
const val CARDS_PER_TURN = 6

class IndigoGame(firstStarts: Boolean) {
    private val player1: Player = Human()
    private val player2: Player = Computer()

    private val firstPlayer = if (firstStarts) player1 else player2

    private var lastRewardedPlayer: Player? = null
    private var currentPlayer = firstPlayer

    private val deck: Deck = Deck()
    private val table: Table = Table()

    fun play() {
        init()
        println("Initial cards on the table: $table\n")
        deal()

        println(table.toShortString())
        try {
            playLoop()
        } catch (gte: GameTerminatedException) {
            // do nothing
        }

        println("Game Over")
    }

    private fun playLoop() {
        while (!isOver()) {
            val topCard = table.top()
            val card = currentPlayer.pickCard(table.top())
            if (topCard != null && card.isSameRankOrSuit(topCard)) {
                // current player wins cards
                table.add(card)
                currentPlayer.takeReward(table)
                lastRewardedPlayer = currentPlayer

                println("${currentPlayer.getName()} wins cards")
                printScores()
            } else {
                table.add(card)
            }

            if (!player1.hasCards() && !player2.hasCards()) {
                deal()
            }

            currentPlayer = oppositePlayer()
            println("\n${table.toShortString()}")
        }

        if (!table.isEmpty()) {
            lastRewardedPlayer?.takeReward(table)
        }
        printScores(final = true)
    }

    private fun isOver() = deck.isEmpty() && !player1.hasCards() && !player2.hasCards()

    private fun init() {
        table.clear()

        player1.reset()
        player2.reset()

        deck.reset()
        deck.shuffle()

        deck.giveLast(CARDS_ON_TABLE, table)
    }

    private fun deal() {
        player1.takeCards(min(CARDS_PER_TURN, deck.size()), deck)
        player2.takeCards(min(CARDS_PER_TURN, deck.size()), deck)
    }

    private fun printScores(final: Boolean = false) {
        var score1 = player1.getScore()
        val count1 = player1.wonCards()

        var score2 = player2.getScore()
        val count2 = player2.wonCards()

        if (final) {
            if (count1 == count2) {
                if (firstPlayer == player1) score1 += 3 else score2 += 3
            } else if (count1 > count2) {
                score1 += 3
            } else {
                score2 += 3
            }
        }

        println("Score: ${player1.getName()} $score1 - ${player2.getName()} $score2")
        println("Cards: ${player1.getName()} $count1 - ${player2.getName()} $count2")
    }

    private fun oppositePlayer(): Player = if (currentPlayer == player1) player2 else player1
}

fun main() {
    println("Indigo Card Game")

    val playFirst = askPlayFirst()

    IndigoGame(playFirst).play()
}

fun askPlayFirst(): Boolean {
    while (true) {
        println("Play first?")
        when (readln()) {
            "yes" -> return true
            "no" -> return false
        }
    }
}