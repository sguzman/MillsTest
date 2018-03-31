package com.github.sguzman.scraper.stream.lord

import java.io.{File, FileInputStream, FileOutputStream}
import java.net.SocketTimeoutException

import com.github.sguzman.scraper.stream.lord.http.HttpCache
import com.github.sguzman.scraper.stream.lord.items._
import com.google.protobuf.ByteString
import net.ruippeixotog.scalascraper.browser.{Browser, JsoupBrowser}
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.model.Element
import net.ruippeixotog.scalascraper.scraper.ContentExtractors.{element, elementList}
import scalaj.http.Http

import scala.util.{Failure, Success}

object Main{
  var httpCache = {
    val file = new File("./http.data")
    if (!file.exists) file.createNewFile()
    val in = new FileInputStream("./http.data")
    val cache = HttpCache.parseFrom(in)
    in.close()
    cache
  }

  def writeHttp(): Unit = {
    val file = new File("./http.data")
    val out = new FileOutputStream(file)
    httpCache.writeTo(out)
    out.close()
  }

  @volatile var itemCache = {
    val file = new File("./item.data")
    if (!file.exists) file.createNewFile()
    val in = new FileInputStream("./item.data")
    val cache = items.Items.parseFrom(in)
    in.close()
    cache
  }

  def writeItem(): Unit = {
    val file = new File("./item.data")
    val out = new FileOutputStream(file)
    itemCache.writeTo(out)
    out.close()
  }

  Runtime.getRuntime.addShutdownHook(new Thread {
    override def run(): Unit = {
      writeItem()
      writeHttp()
    }
  })

  def retryHttpGet(url: String): String = util.Try(Http(url).asString) match {
    case Success(v) => v.body
    case Failure(e) => e match {
      case _: SocketTimeoutException => retryHttpGet(url)
      case _ => throw new Exception(s"Url: $url; ${e.getMessage}")
    }
  }

  implicit final class DocWrap(doc: Browser#DocumentType) {
    def map(s: String): Element = doc.>?>(element(s)) match {
      case Some(v) => v
      case None => throw new Exception(s)
    }

    def flatMap(s: String): List[Element] = doc.>?>(elementList(s)) match {
      case Some(v) => v
      case None => throw new Exception(s)
    }
  }

  implicit final class StrWrap(str: String) {
    def enum = str.toLowerCase match {
      case "sub" | "subbed" => 1
      case "dub" | "dubbed" => 2
      case "raw" => 3
      case _ => throw new Exception(str)
    }

    def doc = JsoupBrowser().parseString(str)
  }

  trait Cacheable[B] {
    def contains(s: String): Boolean
    def apply(s: String): B
    def put(s: String, b: B): Unit
  }

  def get[A <: Cacheable[B], B](url: String, cache: A) (f: Browser#DocumentType => B): B =
    if (cache.contains(url)) {
      val value = cache.apply(url)
      scribe.info(s"Hit cache for key $url -> $value")
      value
    }
    else if (httpCache.cache.contains(url)) {
      scribe.info(s"Missed item cache for $url but hit http cache")
      val html = httpCache.cache(url)
      val result = f(JsoupBrowser().parseString(Brotli.decompress(html.toByteArray)))
      scribe.info(s"Got key $url -> $result")
      cache.put(url, result)
      result
    } else {
      scribe.info(s"Missed http cache... calling $url")
      val html = retryHttpGet(url)
      httpCache = httpCache.addCache((url, ByteString.copyFrom(Brotli.compress(html))))
      val result = f(JsoupBrowser().parseString(html))
      scribe.info(s"After HTTP request, got key $url -> $result")
      cache.put(url, result)
      result
    }

  def get[A](s: String)
             (cont: String => Boolean)
             (appl: String => A)
             (pu: (String, A) => Unit)
             (f: Browser#DocumentType => A): A =
    get[Cacheable[A], A](s, new Cacheable[A] {
      override def contains(s: String): Boolean = cont(s)
      override def apply(s: String): A = appl(s)
      override def put(s: String, b: A): Unit = pu(s, b)
  }) (f)

  def main(args: Array[String]): Unit = {
    locally {
      val seed = "https://www.animebam.net/series"
      val select = "div.container > div.row > div.col-md-6 > div.panel.panel-default > div.panel-footer > ul.series_alpha > li > a[href]"
      val animes = get(seed
      )(s => itemCache.links.nonEmpty
      )(s => itemCache.links
      )((s, b) => itemCache = itemCache.addAllLinks(b)
      )(_.flatMap(select).map(_.attr("href")).toSeq)
    }

      locally {
        itemCache.links.par
        .map{url =>
          val title = "div.media > div.media-body > div.first > h1"
          val img = "div.media > a.pull-left > img[src]"
          val desc = "body > div.fattynav > div > div > div > div > div.second > p.ptext"
          val genres = "ul.tagcat > li > a[href]"

          val epsLink = "ul.newmanga > li > div > a[href]"

          val epsTitle = "ul.newmanga > li > div > i.anititle"
          val epsType = "ul.newmanga > li > div"

          get[Show](s"https://www.animebam.net$url") (itemCache.cache.contains) (itemCache.cache) ((s, b) => itemCache = itemCache.addCache((s, b))) {doc =>
            val links = doc.flatMap(epsLink)
            val titles = doc.flatMap(epsTitle)
            val types: Seq[Set[Int]] = doc.flatMap(epsType).map(a => Set(a.innerHtml.doc.flatMap("i.btn-xs").map(_.innerHtml.enum): _*))

            Show(
              doc.map(title).innerHtml,
              doc.map(img).attr("src"),
              doc.map(desc).innerHtml,
              doc.flatMap(genres).map(_.innerHtml),
              doc.flatMap(epsLink)
                .zipWithIndex
                .map(a =>
                  Episode(
                    a._1.attr("href"),
                    titles(a._2).innerHtml,
                    types(a._2).contains(1),
                    types(a._2).contains(2),
                    types(a._2).contains(3)
                  )
                )
            )
          }
        }
      }

    locally {
      itemCache.cache.values.par
        .flatMap{a =>
          a.eps.map{b =>
            val url = s"https://www.animebam.net${b.link}"
            val sub = "#subbed-ABVideo"
            val dub = "#dubbed-ABVideo"
            val raw = "#raw-ABVideo"
            get[Ep](url) (itemCache.episode.contains) (itemCache.episode) ((s, b) => itemCache.episode(s, b)) {doc =>
              Ep(
                if (b.sub) doc.map("iframe#subbed-ABVideo[src]").attr("src") else "",
                if (b.dub) doc.map("iframe#dubbed-ABVideo[src]").attr("src") else "",
                if (b.raw) doc.map("iframe#raw-ABVideo[src]").attr("src") else ""
              )
            }
          }
        }
    }

    scribe.info("done")
  }
}
