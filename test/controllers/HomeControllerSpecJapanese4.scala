/*
 * Copyright 2021 Linked Ideal LLC.[https://linked-ideal.com/]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import akka.util.Timeout
import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.data.accessor.neo4j.Neo4JAccessor
import com.ideal.linked.toposoid.common.{CLAIM, PREMISE, ToposoidUtils}
import com.ideal.linked.toposoid.knowledgebase.regist.model.{Knowledge, PropositionRelation, Reference}
import com.ideal.linked.toposoid.protocol.model.base.AnalyzedSentenceObjects
import com.ideal.linked.toposoid.protocol.model.parser.{InputSentenceForParser, KnowledgeForParser, KnowledgeSentenceSetForParser}
import com.ideal.linked.toposoid.sentence.transformer.neo4j.Sentence2Neo4jTransformer
import com.ideal.linked.toposoid.vectorizer.FeatureVectorizer
import controllers.TestUtils.{addImageInfoToAnalyzedSentenceObjects, getImageInfo, getKnowledge, getUUID, registSingleClaim}
import io.jvm.uuid.UUID
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Play.materializer
import play.api.http.Status.OK
import play.api.libs.json.Json
import play.api.test.Helpers.{POST, contentType, status, _}
import play.api.test._

import scala.concurrent.duration.DurationInt

class HomeControllerSpecJapanese4 extends PlaySpec with BeforeAndAfter with BeforeAndAfterAll with GuiceOneAppPerSuite with DefaultAwaitTimeout with Injecting {

  before {
    Neo4JAccessor.delete()
    ToposoidUtils.callComponent("{}", conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_PORT"), "createSchema")
    ToposoidUtils.callComponent("{}", conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_PORT"), "createSchema")
    Thread.sleep(1000)
  }

  override def beforeAll(): Unit = {
    Neo4JAccessor.delete()
  }

  override def afterAll(): Unit = {
    Neo4JAccessor.delete()
  }

  override implicit def defaultAwaitTimeout: Timeout = 600.seconds

  val controller: HomeController = inject[HomeController]

  val sentenceA = "猫が２匹います。"
  val referenceA = Reference(url = "", surface = "猫が", surfaceIndex = 0, isWholeSentence = false,
    originalUrlOrReference = "http://images.cocodataset.org/val2017/000000039769.jpg")
  val imageBoxInfoA = ImageBoxInfo(x = 11, y = 11, weight = 466, height = 310)

  val sentenceB = "犬が１匹います。"
  val referenceB = Reference(url = "", surface = "犬が", surfaceIndex = 0, isWholeSentence = false,
    originalUrlOrReference = "http://images.cocodataset.org/train2017/000000428746.jpg")
  val imageBoxInfoB = ImageBoxInfo(x = 77, y = 98, weight = 433, height = 222)

  val sentenceC = "トラックが一台止まっています。"
  val referenceC = Reference(url = "", surface = "トラックが", surfaceIndex = 0, isWholeSentence = false,
    originalUrlOrReference = "https://farm8.staticflickr.com/7103/7210629614_5a388d9a9c_z.jpg")
  val imageBoxInfoC = ImageBoxInfo(x = 23, y = 25, weight = 601, height = 341)

  val sentenceD = "軍用機が2機飛んでいます。"
  val referenceD = Reference(url = "", surface = "軍用機が", surfaceIndex = 0, isWholeSentence = false,
    originalUrlOrReference = "https://farm2.staticflickr.com/1070/5110702674_350f5b367d_z.jpg")
  val imageBoxInfoD = ImageBoxInfo(x = 223, y = 108, weight = 140, height = 205)

  val paraphraseA = "ペットが２匹います。"
  val referenceParaA = Reference(url = "", surface = "ペットが", surfaceIndex = 0, isWholeSentence = false,
    originalUrlOrReference = "http://images.cocodataset.org/val2017/000000039769.jpg")
  val imageBoxInfoParaA = ImageBoxInfo(x = 11, y = 11, weight = 466, height = 310)

  val paraphraseB = "動物が１匹います。"
  val referenceParaB = Reference(url = "", surface = "動物が", surfaceIndex = 0, isWholeSentence = false,
    originalUrlOrReference = "http://images.cocodataset.org/train2017/000000428746.jpg")
  val imageBoxInfoParaB = ImageBoxInfo(x = 77, y = 98, weight = 433, height = 222)

  val paraphraseC = "大型車が一台止まっています。"
  val referenceParaC = Reference(url = "", surface = "大型車が", surfaceIndex = 0, isWholeSentence = false,
    originalUrlOrReference = "https://farm8.staticflickr.com/7103/7210629614_5a388d9a9c_z.jpg")
  val imageBoxInfoParaC = ImageBoxInfo(x = 23, y = 25, weight = 601, height = 341)

  val paraphraseD = "飛行機が2機飛んでいます。"
  val referenceParaD = Reference(url = "", surface = "飛行機が", surfaceIndex = 0, isWholeSentence = false,
    originalUrlOrReference = "https://farm2.staticflickr.com/1070/5110702674_350f5b367d_z.jpg")
  val imageBoxInfoParaD = ImageBoxInfo(x = 223, y = 108, weight = 140, height = 205)

  val lang = "ja_JP"
  
  "The specification31" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val propositionId2 = getUUID()
      val sentenceId1 = getUUID()
      val sentenceId2 = getUUID()
      val knowledge1 = getKnowledge(lang=lang, sentence=sentenceA, reference=referenceA, imageBoxInfo=imageBoxInfoA)
      val knowledge2 = getKnowledge(lang=lang, sentence=sentenceB, reference=referenceB, imageBoxInfo=imageBoxInfoB)
      //val knowledge3 =  getKnowledge(lang=lang, sentence=sentenceC, reference=referenceC, imageBoxInfo=imageBoxInfoC)
      //val knowledge4 = getKnowledge(lang=lang, sentence=sentenceD, reference=referenceD, imageBoxInfo=imageBoxInfoD)

      val paraphrase1 = getKnowledge(lang=lang, sentence=paraphraseA, reference=referenceParaA, imageBoxInfo=imageBoxInfoParaA)
      val paraphrase2 = getKnowledge(lang=lang, sentence=paraphraseB, reference=referenceParaB, imageBoxInfo=imageBoxInfoParaB)
      val paraphrase3 = getKnowledge(lang=lang, sentence=paraphraseC, reference=referenceParaC, imageBoxInfo=imageBoxInfoParaC)
      val paraphrase4 = getKnowledge(lang=lang, sentence=paraphraseD, reference=referenceParaD, imageBoxInfo=imageBoxInfoParaD)

      registSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1))
      registSingleClaim(KnowledgeForParser(propositionId2, sentenceId2, knowledge2))
      val propositionIdForInference = getUUID()
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase1), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase2))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase3), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase4))
      val inputSentence = Json.toJson(InputSentenceForParser(premiseKnowledge, claimKnowledge)).toString()

      val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, List(getImageInfo(referenceParaA, imageBoxInfoParaA), getImageInfo(referenceParaB, imageBoxInfoParaB), getImageInfo(referenceParaC, imageBoxInfoParaC), getImageInfo(referenceParaD, imageBoxInfoParaD)))
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json")
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(PREMISE.index) && x.deductionResult.status).size == 2)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(CLAIM.index) && x.deductionResult.status).size == 0)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(CLAIM.index) && x.deductionResult.havePremiseInGivenProposition).size == 0)
    }
  }

  "The specification32" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val propositionId2 = getUUID()
      val sentenceId1 = getUUID()
      val sentenceId2 = getUUID()
      //val knowledge1 = getKnowledge(lang=lang, sentence=sentenceA, reference=referenceA, imageBoxInfo=imageBoxInfoA)
      //val knowledge2 =  getKnowledge(lang=lang, sentence=sentenceB, reference=referenceB, imageBoxInfo=imageBoxInfoB)
      val knowledge3 =  getKnowledge(lang=lang, sentence=sentenceC, reference=referenceC, imageBoxInfo=imageBoxInfoC)
      val knowledge4 = getKnowledge(lang=lang, sentence=sentenceD, reference=referenceD, imageBoxInfo=imageBoxInfoD)

      val paraphrase1 = getKnowledge(lang=lang, sentence=paraphraseA, reference=referenceParaA, imageBoxInfo=imageBoxInfoParaA)
      val paraphrase2 = getKnowledge(lang=lang, sentence=paraphraseB, reference=referenceParaB, imageBoxInfo=imageBoxInfoParaB)
      val paraphrase3 = getKnowledge(lang=lang, sentence=paraphraseC, reference=referenceParaC, imageBoxInfo=imageBoxInfoParaC)
      val paraphrase4 = getKnowledge(lang=lang, sentence=paraphraseD, reference=referenceParaD, imageBoxInfo=imageBoxInfoParaD)

      registSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge3))
      registSingleClaim(KnowledgeForParser(propositionId2, sentenceId2, knowledge4))

      val propositionIdForInference = getUUID()
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase1), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase2))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase3), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase4))
      val inputSentence = Json.toJson(InputSentenceForParser(premiseKnowledge, claimKnowledge)).toString()
      val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, List(getImageInfo(referenceParaA, imageBoxInfoParaA), getImageInfo(referenceParaB, imageBoxInfoParaB), getImageInfo(referenceParaC, imageBoxInfoParaC), getImageInfo(referenceParaD, imageBoxInfoParaD)))
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json")
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(PREMISE.index) && x.deductionResult.status).size == 0)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(CLAIM.index) && x.deductionResult.status).size == 2)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(CLAIM.index) && x.deductionResult.havePremiseInGivenProposition).size == 0)
    }
  }

  "The specification33" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val sentenceId1 = getUUID()
      val sentenceId2 = getUUID()
      val  knowledge1 = getKnowledge(lang=lang, sentence=sentenceA, reference=referenceA, imageBoxInfo=imageBoxInfoA)
      //val knowledge2 =  getKnowledge(lang=lang, sentence=sentenceB, reference=referenceB, imageBoxInfo=imageBoxInfoB)
      val knowledge3 =  getKnowledge(lang=lang, sentence=sentenceC, reference=referenceC, imageBoxInfo=imageBoxInfoC)
      //val knowledge4 = getKnowledge(lang=lang, sentence=sentenceD, reference=referenceD, imageBoxInfo=imageBoxInfoD)

      val paraphrase1 = getKnowledge(lang=lang, sentence=paraphraseA, reference=referenceParaA, imageBoxInfo=imageBoxInfoParaA)
      val paraphrase2 = getKnowledge(lang=lang, sentence=paraphraseB, reference=referenceParaB, imageBoxInfo=imageBoxInfoParaB)
      val paraphrase3 = getKnowledge(lang=lang, sentence=paraphraseC, reference=referenceParaC, imageBoxInfo=imageBoxInfoParaC)
      val paraphrase4 = getKnowledge(lang=lang, sentence=paraphraseD, reference=referenceParaD, imageBoxInfo=imageBoxInfoParaD)

      val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
        List(KnowledgeForParser(propositionId1, sentenceId1, knowledge1)),
        List.empty[PropositionRelation],
        List(KnowledgeForParser(propositionId1, sentenceId2, knowledge3)),
        List.empty[PropositionRelation]
      )
      Sentence2Neo4jTransformer.createGraph(knowledgeSentenceSetForParser)

      val propositionIdForInference = getUUID()
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase1), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase2))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase3), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase4))
      val inputSentence = Json.toJson(InputSentenceForParser(premiseKnowledge, claimKnowledge)).toString()

      val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, List(getImageInfo(referenceParaA, imageBoxInfoParaA), getImageInfo(referenceParaB, imageBoxInfoParaB), getImageInfo(referenceParaC, imageBoxInfoParaC), getImageInfo(referenceParaD, imageBoxInfoParaD)))
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json")
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(PREMISE.index) && x.deductionResult.status).size == 0)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(CLAIM.index) && x.deductionResult.status).size == 0)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(CLAIM.index) && x.deductionResult.havePremiseInGivenProposition).size == 0)
    }
  }

  "The specification34" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val sentenceId1 = getUUID()
      val sentenceId2 = getUUID()
      val sentenceId3 = getUUID()
      val knowledge1 = getKnowledge(lang=lang, sentence=sentenceA, reference=referenceA, imageBoxInfo=imageBoxInfoA)
      val knowledge2 =  getKnowledge(lang=lang, sentence=sentenceB, reference=referenceB, imageBoxInfo=imageBoxInfoB)
      val knowledge3 =  getKnowledge(lang=lang, sentence=sentenceC, reference=referenceC, imageBoxInfo=imageBoxInfoC)
      //val knowledge4 = getKnowledge(lang=lang, sentence=sentenceD, reference=referenceD, imageBoxInfo=imageBoxInfoD)

      val paraphrase1 = getKnowledge(lang=lang, sentence=paraphraseA, reference=referenceParaA, imageBoxInfo=imageBoxInfoParaA)
      val paraphrase2 = getKnowledge(lang=lang, sentence=paraphraseB, reference=referenceParaB, imageBoxInfo=imageBoxInfoParaB)
      val paraphrase3 = getKnowledge(lang=lang, sentence=paraphraseC, reference=referenceParaC, imageBoxInfo=imageBoxInfoParaC)
      val paraphrase4 = getKnowledge(lang=lang, sentence=paraphraseD, reference=referenceParaD, imageBoxInfo=imageBoxInfoParaD)

      val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
        List(KnowledgeForParser(propositionId1, sentenceId1, knowledge1), KnowledgeForParser(propositionId1, sentenceId2, knowledge2)),
        List(PropositionRelation("AND", 0,1)),
        List(KnowledgeForParser(propositionId1, sentenceId3, knowledge3)),
        List.empty[PropositionRelation])
      Sentence2Neo4jTransformer.createGraph(knowledgeSentenceSetForParser)
      val propositionIdForInference = getUUID()
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase1), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase2))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase3), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase4))
      val inputSentence = Json.toJson(InputSentenceForParser(premiseKnowledge, claimKnowledge)).toString()

      val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, List(getImageInfo(referenceParaA, imageBoxInfoParaA), getImageInfo(referenceParaB, imageBoxInfoParaB), getImageInfo(referenceParaC, imageBoxInfoParaC), getImageInfo(referenceParaD, imageBoxInfoParaD)))
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json")
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(PREMISE.index) && x.deductionResult.status).size == 0)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(CLAIM.index) && x.deductionResult.status).size == 0)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(CLAIM.index) && x.deductionResult.havePremiseInGivenProposition).size == 0)
    }
  }

  "The specification35" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val sentenceId1 = getUUID()
      val sentenceId2 = getUUID()
      val sentenceId3 = getUUID()
      val knowledge1 = getKnowledge(lang=lang, sentence=sentenceA, reference=referenceA, imageBoxInfo=imageBoxInfoA)
      //val knowledge2 =  getKnowledge(lang=lang, sentence=sentenceB, reference=referenceB, imageBoxInfo=imageBoxInfoB)
      val knowledge3 =  getKnowledge(lang=lang, sentence=sentenceC, reference=referenceC, imageBoxInfo=imageBoxInfoC)
      val knowledge4 = getKnowledge(lang=lang, sentence=sentenceD, reference=referenceD, imageBoxInfo=imageBoxInfoD)

      val paraphrase1 = getKnowledge(lang=lang, sentence=paraphraseA, reference=referenceParaA, imageBoxInfo=imageBoxInfoParaA)
      val paraphrase2 = getKnowledge(lang=lang, sentence=paraphraseB, reference=referenceParaB, imageBoxInfo=imageBoxInfoParaB)
      val paraphrase3 = getKnowledge(lang=lang, sentence=paraphraseC, reference=referenceParaC, imageBoxInfo=imageBoxInfoParaC)
      val paraphrase4 = getKnowledge(lang=lang, sentence=paraphraseD, reference=referenceParaD, imageBoxInfo=imageBoxInfoParaD)

      val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
        List(KnowledgeForParser(propositionId1, sentenceId1, knowledge1)),
        List.empty[PropositionRelation],
        List(KnowledgeForParser(propositionId1, sentenceId2, knowledge3), KnowledgeForParser(propositionId1, sentenceId3, knowledge4)),
        List(PropositionRelation("AND", 0,1)))
      Sentence2Neo4jTransformer.createGraph(knowledgeSentenceSetForParser)
      val propositionIdForInference = getUUID()
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase1), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase2))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase3), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase4))
      val inputSentence = Json.toJson(InputSentenceForParser(premiseKnowledge, claimKnowledge)).toString()

      val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, List(getImageInfo(referenceParaA, imageBoxInfoParaA), getImageInfo(referenceParaB, imageBoxInfoParaB), getImageInfo(referenceParaC, imageBoxInfoParaC), getImageInfo(referenceParaD, imageBoxInfoParaD)))
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json")
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(PREMISE.index) && x.deductionResult.status).size == 0)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(CLAIM.index) && x.deductionResult.status).size == 0)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(CLAIM.index) && x.deductionResult.havePremiseInGivenProposition).size == 0)
    }
  }

  "The specification36" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val sentenceId1 = getUUID()
      val sentenceId2 = getUUID()
      val sentenceId3 = getUUID()
      val sentenceId4 = getUUID()
      val knowledge1 = getKnowledge(lang=lang, sentence=sentenceA, reference=referenceA, imageBoxInfo=imageBoxInfoA)
      val knowledge2 =  getKnowledge(lang=lang, sentence=sentenceB, reference=referenceB, imageBoxInfo=imageBoxInfoB)
      val knowledge3 =  getKnowledge(lang=lang, sentence=sentenceC, reference=referenceC, imageBoxInfo=imageBoxInfoC)
      val knowledge4 = getKnowledge(lang=lang, sentence=sentenceD, reference=referenceD, imageBoxInfo=imageBoxInfoD)

      val paraphrase1 = getKnowledge(lang=lang, sentence=paraphraseA, reference=referenceParaA, imageBoxInfo=imageBoxInfoParaA)
      val paraphrase2 = getKnowledge(lang=lang, sentence=paraphraseB, reference=referenceParaB, imageBoxInfo=imageBoxInfoParaB)
      val paraphrase3 = getKnowledge(lang=lang, sentence=paraphraseC, reference=referenceParaC, imageBoxInfo=imageBoxInfoParaC)
      val paraphrase4 = getKnowledge(lang=lang, sentence=paraphraseD, reference=referenceParaD, imageBoxInfo=imageBoxInfoParaD)

      val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
        List(KnowledgeForParser(propositionId1, sentenceId1, knowledge1), KnowledgeForParser(propositionId1, sentenceId2, knowledge2)),
        List(PropositionRelation("AND", 0,1)),
        List(KnowledgeForParser(propositionId1, sentenceId3, knowledge3), KnowledgeForParser(propositionId1, sentenceId4, knowledge4)),
        List(PropositionRelation("AND", 0,1)))
      Sentence2Neo4jTransformer.createGraph(knowledgeSentenceSetForParser)
      val propositionIdForInference = getUUID()
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase1), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase2))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase3), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase4))
      val inputSentence = Json.toJson(InputSentenceForParser(premiseKnowledge, claimKnowledge)).toString()
      val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, List(getImageInfo(referenceParaA, imageBoxInfoParaA), getImageInfo(referenceParaB, imageBoxInfoParaB), getImageInfo(referenceParaC, imageBoxInfoParaC), getImageInfo(referenceParaD, imageBoxInfoParaD)))
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json")
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(PREMISE.index) && x.deductionResult.status).size == 0)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(CLAIM.index) && x.deductionResult.status).size == 0)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(CLAIM.index) && x.deductionResult.havePremiseInGivenProposition).size == 0)
    }
  }

  "The specification37" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val propositionId2 = getUUID()
      val propositionId3 = getUUID()
      val sentenceId1 = getUUID()
      val sentenceId2 = getUUID()
      val sentenceId3 = getUUID()
      val sentenceId4 = getUUID()
      val sentenceId5 = getUUID()
      val sentenceId6 = getUUID()
      val knowledge1 = getKnowledge(lang=lang, sentence=sentenceA, reference=referenceA, imageBoxInfo=imageBoxInfoA)
      val knowledge2 = getKnowledge(lang=lang, sentence=sentenceB, reference=referenceB, imageBoxInfo=imageBoxInfoB)
      val knowledge3 = getKnowledge(lang=lang, sentence=sentenceC, reference=referenceC, imageBoxInfo=imageBoxInfoC)
      val knowledge4 = getKnowledge(lang=lang, sentence=sentenceD, reference=referenceD, imageBoxInfo=imageBoxInfoD)

      val paraphrase1 = getKnowledge(lang=lang, sentence=paraphraseA, reference=referenceParaA, imageBoxInfo=imageBoxInfoParaA)
      val paraphrase2 = getKnowledge(lang=lang, sentence=paraphraseB, reference=referenceParaB, imageBoxInfo=imageBoxInfoParaB)
      val paraphrase3 = getKnowledge(lang=lang, sentence=paraphraseC, reference=referenceParaC, imageBoxInfo=imageBoxInfoParaC)
      val paraphrase4 = getKnowledge(lang=lang, sentence=paraphraseD, reference=referenceParaD, imageBoxInfo=imageBoxInfoParaD)

      registSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1))
      registSingleClaim(KnowledgeForParser(propositionId2, sentenceId2, knowledge2))

      val knowledge1a = getKnowledge(lang=lang, sentence=sentenceA, reference=referenceA, imageBoxInfo=imageBoxInfoA)
      val knowledge2a = getKnowledge(lang=lang, sentence=sentenceB, reference=referenceB, imageBoxInfo=imageBoxInfoB)

      val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
        List(KnowledgeForParser(propositionId3, sentenceId3, knowledge1a), KnowledgeForParser(propositionId3, sentenceId4, knowledge2a)),
        List(PropositionRelation("AND", 0,1)),
        List(KnowledgeForParser(propositionId3, sentenceId5, knowledge3), KnowledgeForParser(propositionId3, sentenceId6, knowledge4)),
        List(PropositionRelation("AND", 0,1)))
      Sentence2Neo4jTransformer.createGraph(knowledgeSentenceSetForParser)
      FeatureVectorizer.createVector(knowledgeSentenceSetForParser)
      Thread.sleep(5000)


      val propositionIdForInference = getUUID()
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase1), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase2))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase3), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase4))
      val inputSentence = Json.toJson(InputSentenceForParser(premiseKnowledge, claimKnowledge)).toString()

      val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, List(getImageInfo(referenceParaA, imageBoxInfoParaA), getImageInfo(referenceParaB, imageBoxInfoParaB), getImageInfo(referenceParaC, imageBoxInfoParaC), getImageInfo(referenceParaD, imageBoxInfoParaD)))
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json")
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(PREMISE.index) && x.deductionResult.status).size == 2)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(CLAIM.index) && x.deductionResult.status).size == 2)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(CLAIM.index) && x.deductionResult.havePremiseInGivenProposition).size == 2)
    }
  }

  "The specification37A" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val propositionId2 = getUUID()
      val propositionId3 = getUUID()
      val propositionId4 = getUUID()
      val sentenceId1 = getUUID()
      val sentenceId2 = getUUID()
      val sentenceId3 = getUUID()
      val sentenceId4 = getUUID()
      val sentenceId5 = getUUID()
      val sentenceId6 = getUUID()
      val knowledge1 = getKnowledge(lang = lang, sentence = sentenceA, reference = referenceA, imageBoxInfo = imageBoxInfoA)
      val knowledge2 = getKnowledge(lang = lang, sentence = sentenceB, reference = referenceB, imageBoxInfo = imageBoxInfoB)
      val knowledge3 = getKnowledge(lang = lang, sentence = sentenceC, reference = referenceC, imageBoxInfo = imageBoxInfoC)
      val knowledge4 = getKnowledge(lang = lang, sentence = sentenceD, reference = referenceD, imageBoxInfo = imageBoxInfoD)

      val paraphrase1 = getKnowledge(lang = lang, sentence = paraphraseA, reference = referenceParaA, imageBoxInfo = imageBoxInfoParaA)
      val paraphrase2 = getKnowledge(lang = lang, sentence = paraphraseB, reference = referenceParaB, imageBoxInfo = imageBoxInfoParaB)
      val paraphrase3 = getKnowledge(lang = lang, sentence = paraphraseC, reference = referenceParaC, imageBoxInfo = imageBoxInfoParaC)
      val paraphrase4 = getKnowledge(lang = lang, sentence = paraphraseD, reference = referenceParaD, imageBoxInfo = imageBoxInfoParaD)

      registSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1))
      registSingleClaim(KnowledgeForParser(propositionId2, sentenceId2, knowledge2))
      registSingleClaim(KnowledgeForParser(propositionId3, sentenceId3, knowledge3))
      registSingleClaim(KnowledgeForParser(propositionId4, sentenceId4, knowledge4))

      val propositionIdForInference = getUUID()
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase1), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase2))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase3), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase4))
      val inputSentence = Json.toJson(InputSentenceForParser(premiseKnowledge, claimKnowledge)).toString()

      val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, List(getImageInfo(referenceParaA, imageBoxInfoParaA), getImageInfo(referenceParaB, imageBoxInfoParaB), getImageInfo(referenceParaC, imageBoxInfoParaC), getImageInfo(referenceParaD, imageBoxInfoParaD)))
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json")
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(PREMISE.index) && x.deductionResult.status).size == 2)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(CLAIM.index) && x.deductionResult.status).size == 2)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(CLAIM.index) && x.deductionResult.havePremiseInGivenProposition).size == 0)
    }
  }

  "The specification38" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val propositionId2 = getUUID()
      val sentenceId1 = getUUID()
      val sentenceId2 = getUUID()
      val sentenceId3 = getUUID()
      val sentenceId4 = getUUID()
      val sentenceId5 = getUUID()
      val knowledge1 = getKnowledge(lang=lang, sentence=sentenceA, reference=referenceA, imageBoxInfo=imageBoxInfoA)
      val knowledge2 =  getKnowledge(lang=lang, sentence=sentenceB, reference=referenceB, imageBoxInfo=imageBoxInfoB)
      val knowledge3 =  getKnowledge(lang=lang, sentence=sentenceC, reference=referenceC, imageBoxInfo=imageBoxInfoC)
      val knowledge4 = getKnowledge(lang=lang, sentence=sentenceD, reference=referenceD, imageBoxInfo=imageBoxInfoD)

      val paraphrase1 = getKnowledge(lang=lang, sentence=paraphraseA, reference=referenceParaA, imageBoxInfo=imageBoxInfoParaA)
      val paraphrase2 = getKnowledge(lang=lang, sentence=paraphraseB, reference=referenceParaB, imageBoxInfo=imageBoxInfoParaB)
      val paraphrase3 = getKnowledge(lang=lang, sentence=paraphraseC, reference=referenceParaC, imageBoxInfo=imageBoxInfoParaC)
      val paraphrase4 = getKnowledge(lang=lang, sentence=paraphraseD, reference=referenceParaD, imageBoxInfo=imageBoxInfoParaD)

      registSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1))
      val knowledge1a = getKnowledge(lang=lang, sentence=sentenceA, reference=referenceA, imageBoxInfo=imageBoxInfoA)
      val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
        List(KnowledgeForParser(propositionId2, sentenceId2, knowledge1a), KnowledgeForParser(propositionId2, sentenceId3, knowledge2)),
        List(PropositionRelation("AND", 0,1)),
        List(KnowledgeForParser(propositionId2, sentenceId4, knowledge3), KnowledgeForParser(propositionId2, sentenceId5, knowledge4)),
        List(PropositionRelation("AND", 0,1)))
      Sentence2Neo4jTransformer.createGraph(knowledgeSentenceSetForParser)
      FeatureVectorizer.createVector(knowledgeSentenceSetForParser)
      Thread.sleep(5000)

      val propositionIdForInference = getUUID()
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase1), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase2))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase3), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase4))
      val inputSentence = Json.toJson(InputSentenceForParser(premiseKnowledge, claimKnowledge)).toString()

      val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, List(getImageInfo(referenceParaA, imageBoxInfoParaA), getImageInfo(referenceParaB, imageBoxInfoParaB), getImageInfo(referenceParaC, imageBoxInfoParaC), getImageInfo(referenceParaD, imageBoxInfoParaD)))
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json")
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(PREMISE.index) && x.deductionResult.status).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(CLAIM.index) && x.deductionResult.status).size == 0)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(CLAIM.index) && x.deductionResult.havePremiseInGivenProposition).size == 0)
    }
  }

  "The specification39" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val propositionId2 = getUUID()
      val sentenceId1 = getUUID()
      val sentenceId2 = getUUID()
      val sentenceId3 = getUUID()
      val sentenceId4 = getUUID()
      val sentenceId5 = getUUID()
      val knowledge1 = getKnowledge(lang=lang, sentence=sentenceA, reference=referenceA, imageBoxInfo=imageBoxInfoA)
      val knowledge2 = getKnowledge(lang=lang, sentence=sentenceB, reference=referenceB, imageBoxInfo=imageBoxInfoB)
      val knowledge3 = getKnowledge(lang=lang, sentence=sentenceC, reference=referenceC, imageBoxInfo=imageBoxInfoC)
      val knowledge4 = getKnowledge(lang=lang, sentence=sentenceD, reference=referenceD, imageBoxInfo=imageBoxInfoD)

      val paraphrase1 = getKnowledge(lang=lang, sentence=paraphraseA, reference=referenceParaA, imageBoxInfo=imageBoxInfoParaA)
      val paraphrase2 = getKnowledge(lang=lang, sentence=paraphraseB, reference=referenceParaB, imageBoxInfo=imageBoxInfoParaB)
      val paraphrase3 = getKnowledge(lang=lang, sentence=paraphraseC, reference=referenceParaC, imageBoxInfo=imageBoxInfoParaC)
      val paraphrase4 = getKnowledge(lang=lang, sentence=paraphraseD, reference=referenceParaD, imageBoxInfo=imageBoxInfoParaD)

      registSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge3))

      val knowledge3a = getKnowledge(lang=lang, sentence=sentenceC, reference=referenceC, imageBoxInfo=imageBoxInfoC)
      val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
        List(KnowledgeForParser(propositionId2, sentenceId2, knowledge1), KnowledgeForParser(propositionId2, sentenceId3, knowledge2)),
        List(PropositionRelation("AND", 0,1)),
        List(KnowledgeForParser(propositionId2, sentenceId4, knowledge3a), KnowledgeForParser(propositionId2, sentenceId5, knowledge4)),
        List(PropositionRelation("AND", 0,1)))
      Sentence2Neo4jTransformer.createGraph(knowledgeSentenceSetForParser)
      FeatureVectorizer.createVector(knowledgeSentenceSetForParser)
      Thread.sleep(5000)

      val propositionIdForInference = getUUID()
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase1), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase2))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase3), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase4))
      val inputSentence = Json.toJson(InputSentenceForParser(premiseKnowledge, claimKnowledge)).toString()

      val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, List(getImageInfo(referenceParaA, imageBoxInfoParaA), getImageInfo(referenceParaB, imageBoxInfoParaB), getImageInfo(referenceParaC, imageBoxInfoParaC), getImageInfo(referenceParaD, imageBoxInfoParaD)))
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json")
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(PREMISE.index) && x.deductionResult.status).size == 0)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(CLAIM.index) && x.deductionResult.status).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(CLAIM.index) && x.deductionResult.havePremiseInGivenProposition).size == 0)
    }
  }

  "The specification40" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val propositionId2 = getUUID()
      val propositionId3 = getUUID()
      val sentenceId1 = getUUID()
      val sentenceId2 = getUUID()
      val sentenceId3 = getUUID()
      val sentenceId4 = getUUID()
      val sentenceId5 = getUUID()
      val sentenceId6 = getUUID()
      val knowledge1 = getKnowledge(lang=lang, sentence=sentenceA, reference=referenceA, imageBoxInfo=imageBoxInfoA)
      val knowledge2 = getKnowledge(lang=lang, sentence=sentenceB, reference=referenceB, imageBoxInfo=imageBoxInfoB)
      val knowledge3 = getKnowledge(lang=lang, sentence=sentenceC, reference=referenceC, imageBoxInfo=imageBoxInfoC)
      val knowledge4 = getKnowledge(lang=lang, sentence=sentenceD, reference=referenceD, imageBoxInfo=imageBoxInfoD)

      val paraphrase1 = getKnowledge(lang=lang, sentence=paraphraseA, reference=referenceParaA, imageBoxInfo=imageBoxInfoParaA)
      val paraphrase2 = getKnowledge(lang=lang, sentence=paraphraseB, reference=referenceParaB, imageBoxInfo=imageBoxInfoParaB)
      val paraphrase3 = getKnowledge(lang=lang, sentence=paraphraseC, reference=referenceParaC, imageBoxInfo=imageBoxInfoParaC)
      val paraphrase4 = getKnowledge(lang=lang, sentence=paraphraseD, reference=referenceParaD, imageBoxInfo=imageBoxInfoParaD)

      registSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1))
      registSingleClaim(KnowledgeForParser(propositionId2, sentenceId2, knowledge3))

      val knowledge1a = getKnowledge(lang=lang, sentence=sentenceA, reference=referenceA, imageBoxInfo=imageBoxInfoA)
      val knowledge3a = getKnowledge(lang=lang, sentence=sentenceC, reference=referenceC, imageBoxInfo=imageBoxInfoC)

      val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
        List(KnowledgeForParser(propositionId3, sentenceId3, knowledge1a), KnowledgeForParser(propositionId3, sentenceId4, knowledge2)),
        List(PropositionRelation("AND", 0,1)),
        List(KnowledgeForParser(propositionId3, sentenceId5, knowledge3a), KnowledgeForParser(propositionId3, sentenceId6, knowledge4)),
        List(PropositionRelation("AND", 0,1)))
      Sentence2Neo4jTransformer.createGraph(knowledgeSentenceSetForParser)
      FeatureVectorizer.createVector(knowledgeSentenceSetForParser)
      Thread.sleep(5000)

      val propositionIdForInference = getUUID()
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase1), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase2))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase3), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase4))
      val inputSentence = Json.toJson(InputSentenceForParser(premiseKnowledge, claimKnowledge)).toString()
      val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, List(getImageInfo(referenceParaA, imageBoxInfoParaA), getImageInfo(referenceParaB, imageBoxInfoParaB), getImageInfo(referenceParaC, imageBoxInfoParaC), getImageInfo(referenceParaD, imageBoxInfoParaD)))
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json")
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(PREMISE.index) && x.deductionResult.status).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(CLAIM.index) && x.deductionResult.status).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(CLAIM.index) && x.deductionResult.havePremiseInGivenProposition).size == 0)
    }
  }

}
