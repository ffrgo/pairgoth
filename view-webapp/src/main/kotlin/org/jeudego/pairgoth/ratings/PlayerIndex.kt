package org.jeudego.pairgoth.ratings

import com.republicate.kson.Json
import org.apache.lucene.analysis.LowerCaseFilter
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.StoredField
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.queryparser.complexPhrase.ComplexPhraseQueryParser
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.FuzzyQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TermQuery
import org.apache.lucene.store.ByteBuffersDirectory
import org.apache.lucene.store.Directory
import org.apache.lucene.store.NoLockFactory
import org.slf4j.LoggerFactory
import java.util.*

class PlayerIndex {
    companion object {
        val ID = "id"
        val ORIGIN = "origin"
        val NAME = "name"
        val FIRSTNAME = "firstname"
        val TEXT = "text"
        val COUNTRY = "country"

        val MAX_HITS = 20
        val logger = LoggerFactory.getLogger("index")
        val queryParser = ComplexPhraseQueryParser(TEXT, StandardAnalyzer())
    }
    private final val directory: Directory = ByteBuffersDirectory(NoLockFactory.INSTANCE)
    private val reader by lazy { DirectoryReader.open(directory) }
    private val searcher by lazy { IndexSearcher(reader) }

    // helper functions
    fun Json.Object.field(key: String) = getString(key) ?: throw Error("missing $key")
    fun Json.Object.nullableField(key: String) = getString(key) ?: ""

    fun build(players: Json.Array) {
        logger.info("indexing players")
        var count = 0L
        IndexWriter(directory, IndexWriterConfig(StandardAnalyzer()).apply {
            openMode = IndexWriterConfig.OpenMode.CREATE
        }).use { writer ->
            players.forEachIndexed { i, p ->
                val player = p as Json.Object
                val origin = p.getString(ORIGIN) ?: throw Error("unknown origin")
                val text = player.field(NAME).lowercase(Locale.ROOT)
                val doc = Document()
                doc.add(StoredField(ID, i));
                doc.add(StringField(ORIGIN, player.field(ORIGIN).lowercase(Locale.ROOT), Field.Store.NO))
                doc.add(StringField(COUNTRY, player.field(COUNTRY).lowercase(Locale.ROOT), Field.Store.NO))
                doc.add(TextField(TEXT, "${player.field(NAME)} ${player.nullableField(FIRSTNAME)}", Field.Store.NO))
                writer.addDocument(doc);
                ++count
            }
        }
        logger.info("indexed $count players")
    }

    fun match(needle: String, origins: Int, country: String?): List<Int> {
        val terms = needle.lowercase(Locale.ROOT)
            .replace(Regex("([+&|!(){}\\[\\]^\\\\\"~*?:/]|(?<!\\b)-)"), "")
            .split(Regex("[ -_']+"))
            .filter { !it.isEmpty() }
            .map { "$it~" }
            .joinToString(" ")
            .let { if (it.isEmpty()) it else "$it ${it.substring(0, it.length - 1) + "*^4"}" }
        if (terms.isEmpty()) return emptyList()
        logger.info("Search query: $terms")
        val fuzzy = queryParser.parse(terms)
        val activeMask = RatingsManager.activeMask()
        val query = when (origins.countOneBits()) {
            0 -> return emptyList()
            1 -> {
                val filter = TermQuery(Term(ORIGIN, RatingsManager.Ratings.codeOf(origins)))
                BooleanQuery.Builder()
                    .add(fuzzy, BooleanClause.Occur.SHOULD)
                    .add(filter, BooleanClause.Occur.FILTER)
                    .build()
            }
            2 -> {
                if (activeMask.countOneBits() > 2) {
                    val filter =
                        TermQuery(Term(ORIGIN, RatingsManager.Ratings.codeOf((origins xor activeMask) and activeMask)))
                    BooleanQuery.Builder()
                        .add(fuzzy, BooleanClause.Occur.SHOULD)
                        .add(filter, BooleanClause.Occur.MUST_NOT)
                        .build()
                } else fuzzy
            }
            3 -> fuzzy
            else -> throw Error("wrong origins mask")
        }.let {
            if (country == null) it
            else {
                val countryFilter = TermQuery(Term(COUNTRY, country.lowercase(Locale.ROOT)))
                BooleanQuery.Builder()
                    .add(it, BooleanClause.Occur.SHOULD)
                    .add(countryFilter, BooleanClause.Occur.FILTER)
                    .build()
            }
        }
        val docs = searcher.search(query, MAX_HITS)
        return docs.scoreDocs.map { searcher.doc(it.doc).getField(ID).numericValue().toInt() }.toList()
    }
}
