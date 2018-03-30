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
        .asJson.spaces4
    }) (s => decode[List[Anime]](s))

    final case class AnimePage(img: String, p: String, eps: List[String])
    val episodes = anime.map{a =>
        Init.casc(a.link, {doc =>
          AnimePage(
            doc.Map("div.series-container.group > img[src]").attr("src"),
            doc.Map("div.series-container.group > p").text,
            doc.FlatMap("li.list-group-item > div > a[href]").map(_.attr("href"))
          ).asJson.spaces4
        }) (s => decode[AnimePage](s))
    }

    val ignore = Seq(
      "https://ww4.animejolt.com/3-gatsu-no-lion-2nd-season-episode-21/",
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
