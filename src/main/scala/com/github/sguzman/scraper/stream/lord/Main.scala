package com.github.sguzman.scraper.stream.lord

import com.github.sguzman.scraper.stream.lord.Init.DocWrap
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.parser.decode
import net.ruippeixotog.scalascraper.browser.Browser

import scala.util.{Failure, Success}

object Main {
  def casc[A](s: String, proc: Browser#DocumentType => String)(dec: String => Either[io.circe.Error, A]) =
    util.Try{
      Init.cascade(s, proc, dec)
    } match {
      case Success(v) => v
      case Failure(e) => throw new Exception(s"$s; ${e.getMessage}")
    }

  def main(args: Array[String]): Unit = {
    final case class Anime(title: String, link: String)
    val url = "https://ww4.animejolt.com/anime/"

    val anime = Init.cascade(url, {doc =>
      doc.FlatMap(".list-group-item > div > a[href]")
        .map(a => Anime(a.innerHtml, a.attr("href")))
        .asJson.spaces4
    }, s => decode[List[Anime]](s), deleteThis = false)

    final case class AnimePage(img: String, p: String, eps: List[String])
    val episodes = anime.map{a =>
      util.Try{
        Init.cascade(a.link, {doc =>
          AnimePage(
            doc.Map("div.series-container.group > img[src]").attr("src"),
            doc.Map("div.series-container.group > p").text,
            doc.FlatMap("li.list-group-item > div > a[href]").map(_.attr("href"))
          ).asJson.spaces4
        }, s => decode[AnimePage](s))
      } match {
        case Success(v) => v
        case Failure(e) => throw new Exception(s"$a; ${e.getMessage}")
      }
    }

    episodes
      .flatMap(_.eps)
      .map{a =>
        casc(a, doc => doc.Map("iframe#video_frame[src]").attr("src"))(s => Right(s))
      }
  }
}
