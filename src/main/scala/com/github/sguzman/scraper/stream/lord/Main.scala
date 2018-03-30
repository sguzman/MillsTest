package com.github.sguzman.scraper.stream.lord

import java.io.{File, FileInputStream}

import http.HttpCache

object Main{
  val httpCache = {
    val file = new File("./http.data")
    if (!file.exists) file.createNewFile()
    val in = new FileInputStream("./http.data")
    HttpCache.parseFrom(in)
  }

  def main(args: Array[String]): Unit = {
    println("Hello")
  }
}
