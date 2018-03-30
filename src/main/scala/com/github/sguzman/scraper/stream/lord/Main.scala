package com.github.sguzman.scraper.stream.lord

import java.io.{File, FileInputStream, FileOutputStream}
import java.net.SocketTimeoutException

import http.HttpCache
import net.ruippeixotog.scalascraper.browser.{Browser, JsoupBrowser}
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.model.Element
import net.ruippeixotog.scalascraper.scraper.ContentExtractors.{element, elementList}
import scalaj.http.Http

import scala.language.reflectiveCalls
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

  def writeHttp: Unit = {
    val file = new File("./http.data")
    val out = new FileOutputStream(file)
    httpCache.writeTo(out)
    out.close()
  }

  var itemCache = {
    val file = new File("./item.data")
    if (!file.exists) file.createNewFile()
    val in = new FileInputStream("./item.data")
    val cache = items.Items.parseFrom(in)
    in.close()
    cache
  }

  def writeItem: Unit = {
    val file = new File("./http.data")
    val out = new FileOutputStream(file)
    itemCache.writeTo(out)
    out.close()
  }

  Runtime.getRuntime.addShutdownHook(new Thread {
    override def run(): Unit = {
      writeItem
      writeHttp
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

  trait Cacheable[B] {
    def contains(s: String): Boolean
    def apply(s: String): B
    def put(s: String, b: B): Unit
  }

  def get[A <: Cacheable[B], B](url: String, cache: A) (f: Browser#DocumentType => B): B =
    if (cache.contains(url)) {
      val value = cache(url)
      scribe.debug(s"Hit cache for key $url -> $value")
      value
    }
    else if (httpCache.cache.contains(url)) {
      scribe.info(s"Missed item cache for $url")
      val html = httpCache.cache(url)
      val result = f(JsoupBrowser().parseString(html))
      cache.put(url, result)
      get[A, B](url, cache)(f)
    } else {
      scribe.trace(s"Missed http cache... calling $url")
      val html = retryHttpGet(url)
      httpCache = httpCache.addCache((url, html))
      get[A, B](url, cache)(f)
    }

  def main(args: Array[String]): Unit = {
    val seed = "https://www.animebam.net/"
    val items = "div.container > div.row > div.col-md-6 > div.panel.panel-default > div.panel-footer > ul.series_alpha > li > a[href]"
    get[Cacheable[Seq[String]], Seq[String]](seed, new Cacheable[Seq[String]] {
      override def contains(s: String): Boolean = itemCache.links.nonEmpty
      override def apply(s: String): Seq[String] = itemCache.links
      override def put(s: String, b: Seq[String]): Unit = itemCache = itemCache.addAllLinks(b)
    }) (_.flatMap(items).map(_.attr("href")).toSeq)
  }
}
