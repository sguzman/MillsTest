package com.github.sguzman.scraper.stream.lord

import java.io.{File, FileInputStream, FileOutputStream}

import http.HttpCache

object Main{
  val httpCache = {
    val file = new File("./http.data")
    if (!file.exists) file.createNewFile()
    val in = new FileInputStream("./http.data")
    val cache = HttpCache.parseFrom(in)
    in.close()
    cache
  }

  def writeHttp = {
    val file = new File("./http.data")
    val out = new FileOutputStream(file)
    httpCache.writeTo(out)
    out.close()
  }

  val itemCache = {
    val file = new File("./item.data")
    if (!file.exists) file.createNewFile()
    val in = new FileInputStream("./item.data")
    val cache = items.Items.parseFrom(in)
    in.close()
    cache
  }

  def writeItem = {
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

  def main(args: Array[String]): Unit = {
    println("Hello")
  }
}
