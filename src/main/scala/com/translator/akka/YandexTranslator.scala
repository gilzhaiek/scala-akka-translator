package com.translator.akka

import akka.actor.Props
import scalaj.http.{Http, HttpOptions}

case class GetLangsResponse(dirs: List[String], langs: Map[String, String])

case class TranslateResponse(code: Int, lang: String, text: List[String])

object YandexTranslator {
  def props(inLang: String, outLang: String): Props = Props(new YandexTranslator(inLang, outLang))

  val API_KEY = "trnsl.1.1.20190721T202630Z.337f1f655b1ab86d.28a47666f54f1a8054fa47ec9bf4a169d2bf5c69"
  val BASE_URL = "https://translate.yandex.net/api/v1.5/tr.json"
  val GET_LANGS = s"${BASE_URL}/getLangs"
  val TRANSLATE = s"${BASE_URL}/translate"

  def getLangs(langCode: String): Map[String, String] = {
    val response = Http(GET_LANGS)
      .param("key", API_KEY)
      .param("ui", langCode)
      .option(HttpOptions.readTimeout(10000)).asString
    Json.parse[GetLangsResponse](response.body).langs
  }

  def translate(lang: String, text: String): String = {
    val response = Http(TRANSLATE)
      .param("key", API_KEY)
      .param("text", text)
      .param("lang", lang)
      .option(HttpOptions.readTimeout(10000)).asString
    Json.parse[TranslateResponse](response.body).text.mkString(". ")
  }
}

class YandexTranslator(inLang: String, outLang: String) extends Translator(inLang, outLang) {
  override def translate(text: String): String = YandexTranslator.translate(s"$inLang-$outLang", text)
}
