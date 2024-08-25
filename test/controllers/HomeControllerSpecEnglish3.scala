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
import com.ideal.linked.toposoid.common.{CLAIM, PREMISE, TRANSVERSAL_STATE, ToposoidUtils, TransversalState}
import com.ideal.linked.toposoid.knowledgebase.regist.model.{PropositionRelation, Reference}
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

class HomeControllerSpecEnglish3 extends PlaySpec with BeforeAndAfter with BeforeAndAfterAll with GuiceOneAppPerSuite with DefaultAwaitTimeout with Injecting {

  val transversalState:TransversalState = TransversalState(userId="test-user", username="guest", roleId=0, csrfToken = "")
  val transversalStateJson:String = Json.toJson(transversalState).toString()

  before {
    Neo4JAccessor.delete()
    ToposoidUtils.callComponent("{}", conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_PORT"), "createSchema", transversalState)
    ToposoidUtils.callComponent("{}", conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_PORT"), "createSchema", transversalState)
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

  val sentenceA = "There are two cats."
  val referenceA = Reference(url = "", surface = "cats", surfaceIndex = 3, isWholeSentence = false,
    originalUrlOrReference = "http://images.cocodataset.org/val2017/000000039769.jpg")
  val imageBoxInfoA = ImageBoxInfo(x = 11, y = 11, weight = 466, height = 310)

  val sentenceB = "There is a dog."
  val referenceB = Reference(url = "", surface = "dog", surfaceIndex = 3, isWholeSentence = false,
    originalUrlOrReference = "http://images.cocodataset.org/train2017/000000428746.jpg")
  val imageBoxInfoB = ImageBoxInfo(x = 77, y = 98, weight = 433, height = 222)

  val sentenceC = "A large truck is parked."
  val referenceC = Reference(url = "", surface = "truck", surfaceIndex = 2, isWholeSentence = false,
    originalUrlOrReference = "https://farm8.staticflickr.com/7103/7210629614_5a388d9a9c_z.jpg")
  val imageBoxInfoC = ImageBoxInfo(x = 23, y = 25, weight = 601, height = 341)

  val sentenceD = "Two jets are flying."
  val referenceD = Reference(url = "", surface = "jets", surfaceIndex = 1, isWholeSentence = false,
    originalUrlOrReference = "https://farm2.staticflickr.com/1070/5110702674_350f5b367d_z.jpg")
  val imageBoxInfoD = ImageBoxInfo(x = 223, y = 108, weight = 140, height = 205)

  val paraphraseA = "There are two pets."
  val referenceParaA = Reference(url = "", surface = "pets", surfaceIndex = 3, isWholeSentence = false,
    originalUrlOrReference = "http://images.cocodataset.org/val2017/000000039769.jpg")
  val imageBoxInfoParaA = ImageBoxInfo(x = 11, y = 11, weight = 466, height = 310)

  val paraphraseB = "There is a animal"
  val referenceParaB = Reference(url = "", surface = "animal", surfaceIndex = 3, isWholeSentence = false,
    originalUrlOrReference = "http://images.cocodataset.org/train2017/000000428746.jpg")
  val imageBoxInfoParaB = ImageBoxInfo(x = 77, y = 98, weight = 433, height = 222)

  val paraphraseC = "A large vehicle is parked."
  val referenceParaC = Reference(url = "", surface = "vehicle", surfaceIndex = 2, isWholeSentence = false,
    originalUrlOrReference = "https://farm8.staticflickr.com/7103/7210629614_5a388d9a9c_z.jpg")
  val imageBoxInfoParaC = ImageBoxInfo(x = 23, y = 25, weight = 601, height = 341)

  val paraphraseD = "Two planes are flying."
  val referenceParaD = Reference(url = "", surface = "planes", surfaceIndex = 1, isWholeSentence = false,
    originalUrlOrReference = "https://farm2.staticflickr.com/1070/5110702674_350f5b367d_z.jpg")
  val imageBoxInfoParaD = ImageBoxInfo(x = 223, y = 108, weight = 140, height = 205)

  val lang = "en_US"

  "The specification21" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val propositionId2 = getUUID()
      val sentenceId1 = getUUID()
      val sentenceId2 = getUUID()
      //val knowledge1 = getKnowledge(lang=lang, sentence=sentenceA, reference=referenceA, imageBoxInfo=imageBoxInfoA, transversalState)
      val knowledge2 = getKnowledge(lang=lang, sentence=sentenceB, reference=referenceB, imageBoxInfo=imageBoxInfoB, transversalState)
      val knowledge3 = getKnowledge(lang=lang, sentence=sentenceC, reference=referenceC, imageBoxInfo=imageBoxInfoC, transversalState)

      val paraphrase1 = getKnowledge(lang=lang, sentence=paraphraseA, reference=referenceParaA, imageBoxInfo=imageBoxInfoParaA, transversalState)
      val paraphrase2 = getKnowledge(lang=lang, sentence=paraphraseB, reference=referenceParaB, imageBoxInfo=imageBoxInfoParaB, transversalState)
      val paraphrase3 = getKnowledge(lang=lang, sentence=paraphraseC, reference=referenceParaC, imageBoxInfo=imageBoxInfoParaC, transversalState)

      registSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge2), transversalState)
      registSingleClaim(KnowledgeForParser(propositionId2, sentenceId2, knowledge3), transversalState)
      val propositionIdForInference = getUUID()
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase1))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase2), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase3))
      val inputSentence = Json.toJson(InputSentenceForParser(premiseKnowledge, claimKnowledge)).toString()

      val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, List(getImageInfo(referenceParaA, imageBoxInfoParaA, transversalState), getImageInfo(referenceParaB, imageBoxInfoParaB, transversalState), getImageInfo(referenceParaC, imageBoxInfoParaC, transversalState)), transversalState)
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
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

  "The specification22" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val sentenceId1 = getUUID()
      val sentenceId2 = getUUID()
      val sentenceId3 = getUUID()
      val knowledge1 = getKnowledge(lang=lang, sentence=sentenceA, reference=referenceA, imageBoxInfo=imageBoxInfoA, transversalState)
      val knowledge2 = getKnowledge(lang=lang, sentence=sentenceB, reference=referenceB, imageBoxInfo=imageBoxInfoB, transversalState)
      val knowledge3 = getKnowledge(lang=lang, sentence=sentenceC, reference=referenceC, imageBoxInfo=imageBoxInfoC, transversalState)

      val paraphrase1 = getKnowledge(lang=lang, sentence=paraphraseA, reference=referenceParaA, imageBoxInfo=imageBoxInfoParaA, transversalState)
      val paraphrase2 = getKnowledge(lang=lang, sentence=paraphraseB, reference=referenceParaB, imageBoxInfo=imageBoxInfoParaB, transversalState)
      val paraphrase3 = getKnowledge(lang=lang, sentence=paraphraseC, reference=referenceParaC, imageBoxInfo=imageBoxInfoParaC, transversalState)

      val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
        List(KnowledgeForParser(propositionId1, sentenceId1, knowledge1)),
        List.empty[PropositionRelation],
        List(KnowledgeForParser(propositionId1, sentenceId2, knowledge2), KnowledgeForParser(propositionId1, sentenceId3, knowledge3)),
        List(PropositionRelation("AND", 0,1)))
      Sentence2Neo4jTransformer.createGraph(knowledgeSentenceSetForParser, transversalState)
      FeatureVectorizer.createVector(knowledgeSentenceSetForParser, transversalState)
      Thread.sleep(5000)

      val propositionIdForInference = getUUID()
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase1))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase2), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase3))
      val inputSentence = Json.toJson(InputSentenceForParser(premiseKnowledge, claimKnowledge)).toString()
      val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, List(getImageInfo(referenceParaA, imageBoxInfoParaA, transversalState), getImageInfo(referenceParaB, imageBoxInfoParaB, transversalState), getImageInfo(referenceParaC, imageBoxInfoParaC, transversalState)), transversalState)
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
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

  "The specification23" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val propositionId2 = getUUID()
      val sentenceId1 = getUUID()
      val sentenceId2 = getUUID()
      val sentenceId3 = getUUID()
      val sentenceId4 = getUUID()
      val knowledge1 = getKnowledge(lang=lang, sentence=sentenceA, reference=referenceA, imageBoxInfo=imageBoxInfoA, transversalState)
      val knowledge2 = getKnowledge(lang=lang, sentence=sentenceB, reference=referenceB, imageBoxInfo=imageBoxInfoB, transversalState)
      val knowledge3 = getKnowledge(lang=lang, sentence=sentenceC, reference=referenceC, imageBoxInfo=imageBoxInfoC, transversalState)

      val paraphrase1 = getKnowledge(lang=lang, sentence=paraphraseA, reference=referenceParaA, imageBoxInfo=imageBoxInfoParaA, transversalState)
      val paraphrase2 = getKnowledge(lang=lang, sentence=paraphraseB, reference=referenceParaB, imageBoxInfo=imageBoxInfoParaB, transversalState)
      val paraphrase3 = getKnowledge(lang=lang, sentence=paraphraseC, reference=referenceParaC, imageBoxInfo=imageBoxInfoParaC, transversalState)

      registSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1), transversalState)
      val knowledge1a = getKnowledge(lang=lang, sentence=sentenceA, reference=referenceA, imageBoxInfo=imageBoxInfoA, transversalState)
      val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
        List(KnowledgeForParser(propositionId2, sentenceId2, knowledge1a)),
        List.empty[PropositionRelation],
        List(KnowledgeForParser(propositionId2, sentenceId3, knowledge2), KnowledgeForParser(propositionId2, sentenceId4, knowledge3)),
        List(PropositionRelation("AND", 0,1)))
      Sentence2Neo4jTransformer.createGraph(knowledgeSentenceSetForParser, transversalState)
      FeatureVectorizer.createVector(knowledgeSentenceSetForParser, transversalState)
      Thread.sleep(5000)

      val propositionIdForInference = getUUID()
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase1))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase2), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase3))
      val inputSentence = Json.toJson(InputSentenceForParser(premiseKnowledge, claimKnowledge)).toString()
      val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, List(getImageInfo(referenceParaA, imageBoxInfoParaA, transversalState), getImageInfo(referenceParaB, imageBoxInfoParaB, transversalState), getImageInfo(referenceParaC, imageBoxInfoParaC, transversalState)), transversalState)
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(PREMISE.index) && x.deductionResult.status).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(CLAIM.index) && x.deductionResult.status).size == 2)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(CLAIM.index) && x.deductionResult.havePremiseInGivenProposition).size == 2)
    }
  }

  "The specification23A" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val propositionId2 = getUUID()
      val propositionId3 = getUUID()
      val sentenceId1 = getUUID()
      val sentenceId2 = getUUID()
      val sentenceId3 = getUUID()
      val sentenceId4 = getUUID()
      val knowledge1 = getKnowledge(lang=lang, sentence=sentenceA, reference=referenceA, imageBoxInfo=imageBoxInfoA, transversalState)
      val knowledge2 = getKnowledge(lang=lang, sentence=sentenceB, reference=referenceB, imageBoxInfo=imageBoxInfoB, transversalState)
      val knowledge3 = getKnowledge(lang=lang, sentence=sentenceC, reference=referenceC, imageBoxInfo=imageBoxInfoC, transversalState)

      val paraphrase1 = getKnowledge(lang=lang, sentence=paraphraseA, reference=referenceParaA, imageBoxInfo=imageBoxInfoParaA, transversalState)
      val paraphrase2 = getKnowledge(lang=lang, sentence=paraphraseB, reference=referenceParaB, imageBoxInfo=imageBoxInfoParaB, transversalState)
      val paraphrase3 = getKnowledge(lang=lang, sentence=paraphraseC, reference=referenceParaC, imageBoxInfo=imageBoxInfoParaC, transversalState)

      registSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1), transversalState)
      registSingleClaim(KnowledgeForParser(propositionId2, sentenceId2, knowledge2), transversalState)
      registSingleClaim(KnowledgeForParser(propositionId3, sentenceId3, knowledge3), transversalState)

      val propositionIdForInference = getUUID()
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase1))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase2), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase3))
      val inputSentence = Json.toJson(InputSentenceForParser(premiseKnowledge, claimKnowledge)).toString()
      val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, List(getImageInfo(referenceParaA, imageBoxInfoParaA, transversalState), getImageInfo(referenceParaB, imageBoxInfoParaB, transversalState), getImageInfo(referenceParaC, imageBoxInfoParaC, transversalState)), transversalState)
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(PREMISE.index) && x.deductionResult.status).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(CLAIM.index) && x.deductionResult.status).size == 2)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(CLAIM.index) && x.deductionResult.havePremiseInGivenProposition).size == 0)
    }
  }

  "The specification24" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val sentenceId1 = getUUID()
      val sentenceId2 = getUUID()
      val knowledge1 = getKnowledge(lang=lang, sentence=sentenceA, reference=referenceA, imageBoxInfo=imageBoxInfoA, transversalState)
      val knowledge2 = getKnowledge(lang=lang, sentence=sentenceB, reference=referenceB, imageBoxInfo=imageBoxInfoB, transversalState)
      //val knowledge3 = getKnowledge(lang=lang, sentence=sentenceC, reference=referenceC, imageBoxInfo=imageBoxInfoC, transversalState)

      val paraphrase1 = getKnowledge(lang=lang, sentence=paraphraseA, reference=referenceParaA, imageBoxInfo=imageBoxInfoParaA, transversalState)
      val paraphrase2 = getKnowledge(lang=lang, sentence=paraphraseB, reference=referenceParaB, imageBoxInfo=imageBoxInfoParaB, transversalState)
      val paraphrase3 = getKnowledge(lang=lang, sentence=paraphraseC, reference=referenceParaC, imageBoxInfo=imageBoxInfoParaC, transversalState)

      val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
        List(KnowledgeForParser(propositionId1, sentenceId1, knowledge1)),
        List.empty[PropositionRelation],
        List(KnowledgeForParser(propositionId1, sentenceId2, knowledge2)),
        List.empty[PropositionRelation])
      Sentence2Neo4jTransformer.createGraph(knowledgeSentenceSetForParser, transversalState)
      FeatureVectorizer.createVector(knowledgeSentenceSetForParser, transversalState)
      Thread.sleep(5000)

      val propositionIdForInference = getUUID()
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase1))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase2), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase3))
      val inputSentence = Json.toJson(InputSentenceForParser(premiseKnowledge, claimKnowledge)).toString()
      val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, List(getImageInfo(referenceParaA, imageBoxInfoParaA, transversalState), getImageInfo(referenceParaB, imageBoxInfoParaB, transversalState), getImageInfo(referenceParaC, imageBoxInfoParaC, transversalState)), transversalState)
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
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

  "The specification25" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val propositionId2 = getUUID()
      val sentenceId1 = getUUID()
      val sentenceId2 = getUUID()
      val sentenceId3 = getUUID()
      val knowledge1 = getKnowledge(lang=lang, sentence=sentenceA, reference=referenceA, imageBoxInfo=imageBoxInfoA, transversalState)
      val knowledge2 = getKnowledge(lang=lang, sentence=sentenceB, reference=referenceB, imageBoxInfo=imageBoxInfoB, transversalState)
      //val knowledge3 = getKnowledge(lang=lang, sentence=sentenceC, reference=referenceC, imageBoxInfo=imageBoxInfoC, transversalState)

      val paraphrase1 = getKnowledge(lang=lang, sentence=paraphraseA, reference=referenceParaA, imageBoxInfo=imageBoxInfoParaA, transversalState)
      val paraphrase2 = getKnowledge(lang=lang, sentence=paraphraseB, reference=referenceParaB, imageBoxInfo=imageBoxInfoParaB, transversalState)
      val paraphrase3 = getKnowledge(lang=lang, sentence=paraphraseC, reference=referenceParaC, imageBoxInfo=imageBoxInfoParaC, transversalState)

      registSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1), transversalState)
      val knowledge1a = getKnowledge(lang=lang, sentence=sentenceA, reference=referenceA, imageBoxInfo=imageBoxInfoA, transversalState)
      val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
        List(KnowledgeForParser(propositionId2, sentenceId2, knowledge1a)),
        List.empty[PropositionRelation],
        List(KnowledgeForParser(propositionId2, sentenceId3, knowledge2)),
        List.empty[PropositionRelation])
      Sentence2Neo4jTransformer.createGraph(knowledgeSentenceSetForParser, transversalState)
      FeatureVectorizer.createVector(knowledgeSentenceSetForParser, transversalState)
      Thread.sleep(5000)

      val propositionIdForInference = getUUID()
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase1))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase2), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase3))
      val inputSentence = Json.toJson(InputSentenceForParser(premiseKnowledge, claimKnowledge)).toString()
      val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, List(getImageInfo(referenceParaA, imageBoxInfoParaA, transversalState), getImageInfo(referenceParaB, imageBoxInfoParaB, transversalState), getImageInfo(referenceParaC, imageBoxInfoParaC, transversalState)), transversalState)
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(PREMISE.index) && x.deductionResult.status).size == 1)
      //assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(CLAIM.index) && x.deductionResult.status).size == 0)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(CLAIM.index) && x.deductionResult.status).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(CLAIM.index) && x.deductionResult.havePremiseInGivenProposition).size == 1)
    }
  }

  "The specification26" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val propositionId2 = getUUID()
      val sentenceId1 = getUUID()
      val sentenceId2 = getUUID()
      val sentenceId3 = getUUID()
      val knowledge1 = getKnowledge(lang=lang, sentence=sentenceA, reference=referenceA, imageBoxInfo=imageBoxInfoA, transversalState)
      //val knowledge2 = getKnowledge(lang=lang, sentence=sentenceB, reference=referenceB, imageBoxInfo=imageBoxInfoB, transversalState)
      val knowledge3 = getKnowledge(lang=lang, sentence=sentenceC, reference=referenceC, imageBoxInfo=imageBoxInfoC, transversalState)

      val paraphrase1 = getKnowledge(lang=lang, sentence=paraphraseA, reference=referenceParaA, imageBoxInfo=imageBoxInfoParaA, transversalState)
      val paraphrase2 = getKnowledge(lang=lang, sentence=paraphraseB, reference=referenceParaB, imageBoxInfo=imageBoxInfoParaB, transversalState)
      val paraphrase3 = getKnowledge(lang=lang, sentence=paraphraseC, reference=referenceParaC, imageBoxInfo=imageBoxInfoParaC, transversalState)

      registSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1), transversalState)
      val knowledge1a = getKnowledge(lang=lang, sentence=sentenceA, reference=referenceA, imageBoxInfo=imageBoxInfoA, transversalState)
      val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
        List(KnowledgeForParser(propositionId2, sentenceId2, knowledge1a)),
        List.empty[PropositionRelation],
        List(KnowledgeForParser(propositionId2, sentenceId3, knowledge3)),
        List.empty[PropositionRelation])
      Sentence2Neo4jTransformer.createGraph(knowledgeSentenceSetForParser, transversalState)
      FeatureVectorizer.createVector(knowledgeSentenceSetForParser, transversalState)
      Thread.sleep(5000)

      val propositionIdForInference = getUUID()
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase1))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase2), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase3))
      val inputSentence = Json.toJson(InputSentenceForParser(premiseKnowledge, claimKnowledge)).toString()
      val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, List(getImageInfo(referenceParaA, imageBoxInfoParaA, transversalState), getImageInfo(referenceParaB, imageBoxInfoParaB, transversalState), getImageInfo(referenceParaC, imageBoxInfoParaC, transversalState)), transversalState)
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(PREMISE.index) && x.deductionResult.status).size == 1)
      //assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(CLAIM.index) && x.deductionResult.status).size == 0)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(CLAIM.index) && x.deductionResult.status).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(CLAIM.index) && x.deductionResult.havePremiseInGivenProposition).size == 1)
    }
  }

  "The specification27" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val sentenceId1 = getUUID()
      val knowledge1 = getKnowledge(lang=lang, sentence=sentenceA, reference=referenceA, imageBoxInfo=imageBoxInfoA, transversalState)
      //val knowledge2 = getKnowledge(lang=lang, sentence=sentenceB, reference=referenceB, imageBoxInfo=imageBoxInfoB, transversalState)
      //val knowledge3 = getKnowledge(lang=lang, sentence=sentenceC, reference=referenceC, imageBoxInfo=imageBoxInfoC, transversalState)
      //val knowledge4 = Knowledge(sentenceD,"ja_JP", "{}", false)

      val paraphrase1 = getKnowledge(lang=lang, sentence=paraphraseA, reference=referenceParaA, imageBoxInfo=imageBoxInfoParaA, transversalState)
      val paraphrase2 = getKnowledge(lang=lang, sentence=paraphraseB, reference=referenceParaB, imageBoxInfo=imageBoxInfoParaB, transversalState)
      val paraphrase3 = getKnowledge(lang=lang, sentence=paraphraseC, reference=referenceParaC, imageBoxInfo=imageBoxInfoParaC, transversalState)
      val paraphrase4 = getKnowledge(lang=lang, sentence=paraphraseD, reference=referenceParaD, imageBoxInfo=imageBoxInfoParaD, transversalState)

      registSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1), transversalState)

      val propositionIdForInference = getUUID()
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase1), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase2))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase3), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase4))
      val inputSentence = Json.toJson(InputSentenceForParser(premiseKnowledge, claimKnowledge)).toString()

      val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, List(getImageInfo(referenceParaA, imageBoxInfoParaA, transversalState), getImageInfo(referenceParaB, imageBoxInfoParaB, transversalState), getImageInfo(referenceParaC, imageBoxInfoParaC, transversalState), getImageInfo(referenceParaD, imageBoxInfoParaD, transversalState)), transversalState)
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
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

  "The specification28" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val sentenceId1 = getUUID()
      //val knowledge1 = getKnowledge(lang=lang, sentence=sentenceA, reference=referenceA, imageBoxInfo=imageBoxInfoA, transversalState)
      val knowledge2 = getKnowledge(lang=lang, sentence=sentenceB, reference=referenceB, imageBoxInfo=imageBoxInfoB, transversalState)
      //val knowledge3 = getKnowledge(lang=lang, sentence=sentenceC, reference=referenceC, imageBoxInfo=imageBoxInfoC, transversalState)
      //val knowledge4 = Knowledge(sentenceD,"ja_JP", "{}", false)

      val paraphrase1 = getKnowledge(lang=lang, sentence=paraphraseA, reference=referenceParaA, imageBoxInfo=imageBoxInfoParaA, transversalState)
      val paraphrase2 = getKnowledge(lang=lang, sentence=paraphraseB, reference=referenceParaB, imageBoxInfo=imageBoxInfoParaB, transversalState)
      val paraphrase3 = getKnowledge(lang=lang, sentence=paraphraseC, reference=referenceParaC, imageBoxInfo=imageBoxInfoParaC, transversalState)
      val paraphrase4 = getKnowledge(lang=lang, sentence=paraphraseD, reference=referenceParaD, imageBoxInfo=imageBoxInfoParaD, transversalState)

      registSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge2), transversalState)

      val propositionIdForInference = getUUID()
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase1), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase2))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase3), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase4))
      val inputSentence = Json.toJson(InputSentenceForParser(premiseKnowledge, claimKnowledge)).toString()

      val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, List(getImageInfo(referenceParaA, imageBoxInfoParaA, transversalState), getImageInfo(referenceParaB, imageBoxInfoParaB, transversalState), getImageInfo(referenceParaC, imageBoxInfoParaC, transversalState), getImageInfo(referenceParaD, imageBoxInfoParaD, transversalState)), transversalState)
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
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

  "The specification29" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val sentenceId1 = getUUID()
      //val knowledge1 = getKnowledge(lang=lang, sentence=sentenceA, reference=referenceA, imageBoxInfo=imageBoxInfoA, transversalState)
      //val knowledge2 = getKnowledge(lang=lang, sentence=sentenceB, reference=referenceB, imageBoxInfo=imageBoxInfoB, transversalState)
      val knowledge3 = getKnowledge(lang=lang, sentence=sentenceC, reference=referenceC, imageBoxInfo=imageBoxInfoC, transversalState)
      //val knowledge4 = Knowledge(sentenceD,"ja_JP", "{}", false)

      val paraphrase1 = getKnowledge(lang=lang, sentence=paraphraseA, reference=referenceParaA, imageBoxInfo=imageBoxInfoParaA, transversalState)
      val paraphrase2 = getKnowledge(lang=lang, sentence=paraphraseB, reference=referenceParaB, imageBoxInfo=imageBoxInfoParaB, transversalState)
      val paraphrase3 = getKnowledge(lang=lang, sentence=paraphraseC, reference=referenceParaC, imageBoxInfo=imageBoxInfoParaC, transversalState)
      val paraphrase4 = getKnowledge(lang=lang, sentence=paraphraseD, reference=referenceParaD, imageBoxInfo=imageBoxInfoParaD, transversalState)

      registSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge3), transversalState)

      val propositionIdForInference = getUUID()
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase1), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase2))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase3), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase4))
      val inputSentence = Json.toJson(InputSentenceForParser(premiseKnowledge, claimKnowledge)).toString()
      val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, List(getImageInfo(referenceParaA, imageBoxInfoParaA, transversalState), getImageInfo(referenceParaB, imageBoxInfoParaB, transversalState), getImageInfo(referenceParaC, imageBoxInfoParaC, transversalState), getImageInfo(referenceParaD, imageBoxInfoParaD, transversalState)), transversalState)
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
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

  "The specification30" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val sentenceId1 = getUUID()
      //val knowledge1 = getKnowledge(lang=lang, sentence=sentenceA, reference=referenceA, imageBoxInfo=imageBoxInfoA, transversalState)
      //val knowledge2 = getKnowledge(lang=lang, sentence=sentenceB, reference=referenceB, imageBoxInfo=imageBoxInfoB, transversalState)
      //val knowledge3 = getKnowledge(lang=lang, sentence=sentenceC, reference=referenceC, imageBoxInfo=imageBoxInfoC, transversalState)
      val knowledge4 = getKnowledge(lang=lang, sentence=sentenceD, reference=referenceD, imageBoxInfo=imageBoxInfoD, transversalState)

      val paraphrase1 = getKnowledge(lang=lang, sentence=paraphraseA, reference=referenceParaA, imageBoxInfo=imageBoxInfoParaA, transversalState)
      val paraphrase2 = getKnowledge(lang=lang, sentence=paraphraseB, reference=referenceParaB, imageBoxInfo=imageBoxInfoParaB, transversalState)
      val paraphrase3 = getKnowledge(lang=lang, sentence=paraphraseC, reference=referenceParaC, imageBoxInfo=imageBoxInfoParaC, transversalState)
      val paraphrase4 = getKnowledge(lang=lang, sentence=paraphraseD, reference=referenceParaD, imageBoxInfo=imageBoxInfoParaD, transversalState)

      registSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge4), transversalState)

      val propositionIdForInference = getUUID()
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase1), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase2))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase3), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase4))
      val inputSentence = Json.toJson(InputSentenceForParser(premiseKnowledge, claimKnowledge)).toString()

      val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, List(getImageInfo(referenceParaA, imageBoxInfoParaA, transversalState), getImageInfo(referenceParaB, imageBoxInfoParaB, transversalState), getImageInfo(referenceParaC, imageBoxInfoParaC, transversalState), getImageInfo(referenceParaD, imageBoxInfoParaD, transversalState)), transversalState)
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
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

}
