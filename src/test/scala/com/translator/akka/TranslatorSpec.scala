package com.translator.akka

import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._


class TranslatorSpec(_system: ActorSystem)
  extends TestKit(_system)
    with Matchers
    with WordSpecLike
    with BeforeAndAfterAll {

  def this() = this(ActorSystem("TranslatorSpec"))

  override def afterAll: Unit = {
    shutdown(system)
  }

  "reply with a reverse letter translation" in {
    val probe = TestProbe()
    val translateActor = system.actorOf(ReverseTranslator.props("en", "ne"))

    translateActor.tell(Translator.TranslateText(1, "hello world"), probe.ref)
    probe.expectMsg(Translator.OnTranslated(1, "dlrow olleh"))
  }

  "reply to yandex translation translation" in {
    val probe = TestProbe()
    val translateActor = system.actorOf(YandexTranslator.props("en", "es"))

    translateActor.tell(Translator.TranslateText(2, "hello world"), probe.ref)
    probe.expectMsg(Translator.OnTranslated(2, "hola mundo"))
  }

  "reply to translation requests" in {
    val probe = TestProbe()
    val reverseTranslatorActor = system.actorOf(ReverseTranslator.props("en", "ne"))

    reverseTranslatorActor.tell(TranslatorManager.RequestTranslator("en", "ne"), probe.ref)
    probe.expectMsg(TranslatorManager.TranslatorRegistered)
    probe.lastSender should ===(reverseTranslatorActor)

    val yandexTranslatorActor = system.actorOf(YandexTranslator.props("en", "es"))

    yandexTranslatorActor.tell(TranslatorManager.RequestTranslator("en", "es"), probe.ref)
    probe.expectMsg(TranslatorManager.TranslatorRegistered)
    probe.lastSender should ===(yandexTranslatorActor)
  }

  "ignore wrong in-out translation requests" in {
    val probe = TestProbe()
    val translateActor = system.actorOf(ReverseTranslator.props("en", "ne"))

    translateActor.tell(TranslatorManager.RequestTranslator("en", "es"), probe.ref)
    probe.expectNoMessage(500.milliseconds)

    translateActor.tell(TranslatorManager.RequestTranslator("sp", "ne"), probe.ref)
    probe.expectNoMessage(500.milliseconds)
  }

  "return an error for not supported translator" in {
    val probe = TestProbe()
    val groupActor = system.actorOf(TranslatorGroup.props("xyz"))

    groupActor.tell(TranslatorManager.RequestTranslator("xyz", "abc"), probe.ref)
    probe.expectMsg(TranslatorManager.TranslatorNotSupported)

    groupActor.tell(TranslatorManager.RequestTranslator("xyz", "zyx"), probe.ref)
    probe.expectMsg(TranslatorManager.TranslatorRegistered)
  }

  "be able to collect temperatures from all active translators" in {
    val probe = TestProbe()
    val groupActor = system.actorOf(TranslatorGroup.props("en"))

    groupActor.tell(TranslatorManager.RequestTranslator("en", "es"), probe.ref)
    probe.expectMsg(TranslatorManager.TranslatorRegistered)
    val englishToSpanish = probe.lastSender

    groupActor.tell(TranslatorManager.RequestTranslator("en", "ru"), probe.ref)
    probe.expectMsg(TranslatorManager.TranslatorRegistered)
    val englishToRussian = probe.lastSender

    groupActor.tell(TranslatorManager.RequestTranslator("en", "ne"), probe.ref)
    probe.expectMsg(TranslatorManager.TranslatorRegistered)
    val reverseEnglish = probe.lastSender

    groupActor.tell(TranslatorManager.RequestTranslator("en", "blable"), probe.ref)
    probe.expectMsg(TranslatorManager.TranslatorNotSupported)

    englishToSpanish.tell(Translator.TranslateText(requestId = 0, "hello world"), probe.ref)
    probe.expectMsg(Translator.OnTranslated(requestId = 0, "hola mundo"))

    englishToRussian.tell(Translator.TranslateText(requestId = 1, "hello world"), probe.ref)
    probe.expectMsg(Translator.OnTranslated(requestId = 1, "Привет мир"))

    reverseEnglish.tell(Translator.TranslateText(requestId = 3, "hello world"), probe.ref)
    probe.expectMsg(Translator.OnTranslated(requestId = 3, "dlrow olleh"))

    groupActor.tell(TranslatorGroup.RequestAllTranslations(requestId = 0, "I love akka translation"), probe.ref)
    probe.expectMsg(
      TranslatorGroup.RespondAllTranslations(
        requestId = 0,
        translations = Map(
          "es" -> TranslatorGroup.Translation("Me encanta akka traducción"),
          "ru" -> TranslatorGroup.Translation("Я люблю акка перевода"),
          "ne" -> TranslatorGroup.Translation("noitalsnart akka evol I"))))
  }
}
