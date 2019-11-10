package com.translator.akka

import akka.actor.Props

object ReverseTranslator {
  def props(inLang: String, outLang: String): Props = Props(new ReverseTranslator(inLang, outLang))
}

class ReverseTranslator(inLang: String, outLang: String) extends Translator(inLang, outLang) {
  override def translate(text: String): String = text.reverse
}
