package com.github.sguzman.scraper.stream.lord

import io.circe.parser.decode
import io.circe.syntax._
import io.circe.generic.auto._
import net.ruippeixotog.scalascraper.browser.Browser
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.scraper.ContentExtractors.elementList

object Main {
  def main(args: Array[String]): Unit = {
    final case class Anime(title: String, link: String)

    val url = "https://ww4.animejolt.com/anime/"
    def proc(doc: Browser#DocumentType): String = {
      doc.>>(elementList(".list-group-item > div > a[href]"))
        .map(a => Anime(a.innerHtml, a.attr("href")))
        .asJson.spaces4
    }

    def dec(s: String) = decode[List[Anime]](s).right.get

    Init.cascade(url, proc, dec)
  }
}
