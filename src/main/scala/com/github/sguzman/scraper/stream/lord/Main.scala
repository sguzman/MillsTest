package com.github.sguzman.scraper.stream.lord

import com.github.sguzman.scraper.stream.lord.Init.DocWrap
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._

object Main {
  def main(args: Array[String]): Unit = {
    final case class Anime(title: String, link: String)
    val url = "https://ww4.animejolt.com/anime/"
    val anime = Init.casc(url, {doc =>
      doc.FlatMap(".list-group-item > div > a[href]")
        .map(a => Anime(a.innerHtml, a.attr("href")))
        .asJson.noSpaces
    }) (s => decode[List[Anime]](s)).par


    final case class AnimePage(img: String, p: String, eps: List[String])
    val episodes = anime.map{a =>
        Init.casc(a.link, {doc =>
          AnimePage(
            doc.Map("div.series-container.group > img[src]").attr("src"),
            doc.Map("div.series-container.group > p").text,
            doc.FlatMap("li.list-group-item > div > a[href]").map(_.attr("href"))
          ).asJson.noSpaces
        }) (s => decode[AnimePage](s))
    }


    val ignore = Seq(
      "https://ww4.animejolt.com/3-gatsu-no-lion-2nd-season-episode-21/",
      "https://ww4.animejolt.com/ane-log-episode-episode-2/",
      "https://ww4.animejolt.com/ao-haru-ride-unwritten-episode-episode-episode-1/",
      "https://ww4.animejolt.com/basilisk-ouka-ninpouchou-episode-13/",
      "https://ww4.animejolt.com/black-clover-tv-episode-26/",
      "https://ww4.animejolt.com/darling-in-the-franxx-episode-12/",
      "https://ww4.animejolt.com/devilman-crybaby-episode-8/",
      "https://ww4.animejolt.com/marchen-madchen-episode-9/",
      "https://ww4.animejolt.com/wake-up-girls-shin-shou-episode-7/",
      "https://ww4.animejolt.com/spiritpact-2-episode-6/",
      "https://ww4.animejolt.com/saiki-kusuo-no--nan-tv-episode-82/",
      "https://ww4.animejolt.com/one-piece-episode-830/",
      "https://ww4.animejolt.com/naruto-shippuuden-movie-7-the-last-episode-episode-episode-1/",
      "https://ww4.animejolt.com/boruto-naruto-next-generations-episode-52/",
      "https://ww4.animejolt.com/devilman-crybaby-episode-7/",
      "https://ww4.animejolt.com/haikyuu-jump-festa-2014-special-episode-episode-1/",
      "https://ww4.animejolt.com/overlord-ii-episode-13/",
      "https://ww4.animejolt.com/toji-no-miko-episode-13/",
      "https://ww4.animejolt.com/takunomi-episode-12/",
      "https://ww4.animejolt.com/devilman-crybaby-episode-6/",
      "https://ww4.animejolt.com/hakata-tonkotsu-ramens-episode-12/",
      "https://ww4.animejolt.com/test-show-episode-episode-15-61120/",
      "https://ww4.animejolt.com/devilman-crybaby-episode-5/",
      "https://ww4.animejolt.com/hakumei-to-mikochi-episode-12/",
      "https://ww4.animejolt.com/devilman-crybaby-episode-4/",
      "https://ww4.animejolt.com/hakyuu-houshin-engi-episode-11/",
      "https://ww4.animejolt.com/devilman-crybaby-episode-3/",
      "https://ww4.animejolt.com/hataraku-onii-san-episode-13/"
    )

    episodes
      .flatMap(_.eps)
      .filter(!_.stripSuffix("/").endsWith("-"))
      .filter(a => !ignore.contains(a))
      .map{a =>
        Init.casc(a, doc => doc.Map("iframe#video_frame[src]").attr("src"))(s => Right(s))
      }
  }
}
