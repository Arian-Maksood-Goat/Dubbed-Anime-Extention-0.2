package eu.kanade.tachiyomi.animeextension.hi.animesalt

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import java.util.Calendar

object AnimeSaltFilters {

    open class QueryPartFilter(
        displayName: String,
        private val vals: Array<Pair<String, String>>,
    ) : AnimeFilter.Select<String>(
        displayName,
        vals.map { it.first }.toTypedArray(),
    ) {
        fun toQueryPart(): String = vals.getOrElse(state) { vals.first() }.second
    }

    open class CheckBoxFilterList(
        name: String,
        values: List<AnimeFilter.CheckBox>,
    ) : AnimeFilter.Group<AnimeFilter.CheckBox>(name, values)

    private class CheckBoxVal(
        name: String,
        state: Boolean = false,
    ) : AnimeFilter.CheckBox(name, state)

    private inline fun <reified R> AnimeFilterList.asQueryPart(): String = filterIsInstance<R>()
        .joinToString("") { (it as QueryPartFilter).toQueryPart() }

    private inline fun <reified R> AnimeFilterList.getFirstOrNull(): R? = filterIsInstance<R>().firstOrNull()

    private inline fun <reified R> AnimeFilterList.parseCheckbox(
        options: Array<Pair<String, String>>,
        name: String,
    ): String {
        val filter = getFirstOrNull<R>() as? CheckBoxFilterList ?: return ""

        val selected = filter.state
            .mapNotNull { checkbox ->
                if (!checkbox.state) return@mapNotNull null

                options.find { it.first == checkbox.name }?.second
            }

        return if (selected.isEmpty()) {
            ""
        } else {
            "&$name[]=${selected.joinToString("&$name[]=")}"
        }
    }

    class SortFilter : QueryPartFilter("Sort order", AnimeSaltFiltersData.SORT)

    class GenreFilter :
        CheckBoxFilterList(
            "Genre",
            AnimeSaltFiltersData.GENRE.map(::CheckBoxVal),
        )

    class SeasonFilter :
        CheckBoxFilterList(
            "Season",
            AnimeSaltFiltersData.SEASON.map(::CheckBoxVal),
        )

    class YearFilter :
        CheckBoxFilterList(
            "Year",
            AnimeSaltFiltersData.YEAR.map(::CheckBoxVal),
        )

    class TypeFilter :
        CheckBoxFilterList(
            "Type",
            AnimeSaltFiltersData.TYPE.map(::CheckBoxVal),
        )

    class StatusFilter :
        CheckBoxFilterList(
            "Status",
            AnimeSaltFiltersData.STATUS.map(::CheckBoxVal),
        )

    class LanguageFilter :
        CheckBoxFilterList(
            "Language",
            AnimeSaltFiltersData.LANGUAGE.map(::CheckBoxVal),
        )

    class RatingFilter :
        CheckBoxFilterList(
            "Rating",
            AnimeSaltFiltersData.RATING.map(::CheckBoxVal),
        )

    val FILTER_LIST
        get() = AnimeFilterList(
            SortFilter(),
            GenreFilter(),
            SeasonFilter(),
            YearFilter(),
            TypeFilter(),
            StatusFilter(),
            LanguageFilter(),
            RatingFilter(),
        )

    data class FilterSearchParams(
        val sort: String = "",
        val genre: String = "",
        val season: String = "",
        val year: String = "",
        val type: String = "",
        val status: String = "",
        val language: String = "",
        val rating: String = "",
    )

    internal fun getSearchParameters(
        filters: AnimeFilterList,
    ): FilterSearchParams {
        if (filters.isEmpty()) return FilterSearchParams()

        return FilterSearchParams(
            sort = filters.asQueryPart<SortFilter>(),
            genre = filters.parseCheckbox<GenreFilter>(
                AnimeSaltFiltersData.GENRE,
                "genre",
            ),
            season = filters.parseCheckbox<SeasonFilter>(
                AnimeSaltFiltersData.SEASON,
                "season",
            ),
            year = filters.parseCheckbox<YearFilter>(
                AnimeSaltFiltersData.YEAR,
                "year",
            ),
            type = filters.parseCheckbox<TypeFilter>(
                AnimeSaltFiltersData.TYPE,
                "term_type",
            ),
            status = filters.parseCheckbox<StatusFilter>(
                AnimeSaltFiltersData.STATUS,
                "status",
            ),
            language = filters.parseCheckbox<LanguageFilter>(
                AnimeSaltFiltersData.LANGUAGE,
                "language",
            ),
            rating = filters.parseCheckbox<RatingFilter>(
                AnimeSaltFiltersData.RATING,
                "rating",
            ),
        )
    }

    private object AnimeSaltFiltersData {

        val SORT = arrayOf(
            "Default" to "default",
            "Latest Updated" to "latest-updated",
            "Latest Added" to "latest-added",
            "Score" to "score",
            "Name A-Z" to "name-az",
            "Release Date" to "release-date",
            "Most Viewed" to "most-viewed",
            "Number of Episodes" to "number_of_episodes",
        )

        val GENRE = arrayOf(
            "Action" to "1",
            "Adventure" to "2",
            "Cars" to "538",
            "Comedy" to "8",
            "Drama" to "62",
            "Fantasy" to "3",
            "Horror" to "222",
            "Isekai" to "74",
            "Mystery" to "57",
            "Romance" to "28",
            "School" to "14",
            "Sci-Fi" to "12",
            "Shounen" to "15",
            "Slice of Life" to "35",
            "Sports" to "29",
            "Supernatural" to "9",
            "Thriller" to "54",
            "Vampire" to "58",
        )

        val SEASON = arrayOf(
            "Fall" to "fall",
            "Summer" to "summer",
            "Spring" to "spring",
            "Winter" to "winter",
        )

        val YEAR = ((Calendar.getInstance().get(Calendar.YEAR) + 1) downTo 1980).map {
            it.toString() to it.toString()
        }.toTypedArray()

        val TYPE = arrayOf(
            "Movie" to "Movie",
            "TV" to "TV",
            "OVA" to "OVA",
            "ONA" to "ONA",
            "Special" to "Special",
            "Music" to "Music",
        )

        val STATUS = arrayOf(
            "Finished Airing" to "finished-airing",
            "Currently Airing" to "currently-airing",
            "Not Yet Aired" to "not-yet-aired",
        )

        val LANGUAGE = arrayOf(
            "Sub" to "sub",
            "Dub" to "dub",
        )

        val RATING = arrayOf(
            "PG - Children" to "PG",
            "PG-13 - Teens 13+" to "PG-13",
            "G - All Ages" to "G",
            "R - Violence & Profanity" to "R",
            "R+ - Mild Nudity" to "R+",
            "Rx - Hentai" to "Rx",
        )
    }
}