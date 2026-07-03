/*
 * Copyright (C) 2025  Linked Ideal LLC.[https://linked-ideal.com/]
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package controllers

import org.apache.pekko.util.Timeout
import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.toposoid.common.{SentenceType, TRANSVERSAL_STATE, ToposoidUtils, TransversalState}
import com.ideal.linked.toposoid.knowledgebase.regist.model.{Knowledge, PropositionRelation, Reference}
import com.ideal.linked.toposoid.protocol.model.base.AnalyzedSentenceObjects
import com.ideal.linked.toposoid.protocol.model.parser.{InputSentenceForParser, KnowledgeForParser, KnowledgeSentenceSetForParser}
import com.ideal.linked.toposoid.test.utils.TestUtils
import controllers.TestUtilsEx.{addImageInfoToAnalyzedSentenceObjects, getImageInfo, getKnowledge, getUUID, registerSingleClaim}
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Play.materializer
import play.api.http.Status.OK
import play.api.libs.json.Json
import play.api.test.Helpers.{POST, contentType, status, _}
import play.api.test._

import scala.concurrent.duration.DurationInt
import com.ideal.linked.toposoid.common.ActionModeType
import com.ideal.linked.toposoid.protocol.model.base.VerifyingEdges
import controllers.TestUtilsEx.getImageInfo2

class HomeControllerSpecEnglishA extends PlaySpec with BeforeAndAfter with BeforeAndAfterAll with GuiceOneAppPerSuite with DefaultAwaitTimeout with Injecting {

  val transversalState:TransversalState = TransversalState(userId="test-user", username="guest", roleId=0, csrfToken = "")
  val transversalStateJson:String = Json.toJson(transversalState).toString()

  before {
    TestUtilsEx.deleteNeo4JAllData(transversalState)
    ToposoidUtils.callComponent("{}", conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_PORT"), "createSchema", transversalState)
    ToposoidUtils.callComponent("{}", conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_PORT"), "createSchema", transversalState)
    Thread.sleep(1000)
  }

  override def beforeAll(): Unit = {
    TestUtilsEx.deleteNeo4JAllData(transversalState)
  }

  override def afterAll(): Unit = {
    TestUtilsEx.deleteNeo4JAllData(transversalState)
  }

  override implicit def defaultAwaitTimeout: Timeout = 600.seconds

  val controller: HomeController = inject[HomeController]

  val sentence1 = "There are two cats."
  val reference1 = Reference(url = "", surface = "cats", surfaceIndex = 3, isWholeSentence = false,
    originalUrlOrReference = "http://images.cocodataset.org/val2017/000000039769.jpg")
  val imageBoxInfo1 = ImageBoxInfo(x = 11, y = 11, weight = 466, height = 310)

  val sentence2 = "There is a dog."
  val reference2 = Reference(url = "", surface = "dog", surfaceIndex = 3, isWholeSentence = false,
    originalUrlOrReference = "http://images.cocodataset.org/train2017/000000428746.jpg")
  val imageBoxInfo2 = ImageBoxInfo(x = 77, y = 98, weight = 433, height = 222)

  val sentence3 = "A large truck is parked."
  val reference3 = Reference(url = "", surface = "truck", surfaceIndex = 2, isWholeSentence = false,
    originalUrlOrReference = "https://farm8.staticflickr.com/7103/7210629614_5a388d9a9c_z.jpg")
  val imageBoxInfo3 = ImageBoxInfo(x = 23, y = 25, weight = 601, height = 341)

  val sentence4 = "Two jets are flying."
  val reference4 = Reference(url = "", surface = "jets", surfaceIndex = 1, isWholeSentence = false,
    originalUrlOrReference = "https://farm2.staticflickr.com/1070/5110702674_350f5b367d_z.jpg")
  val imageBoxInfo4 = ImageBoxInfo(x = 223, y = 108, weight = 140, height = 205)

  val paraphrase1 = "There are two pets."
  val referencePara1Ok = Reference(url = "", surface = "pets", surfaceIndex = 3, isWholeSentence = false,
    originalUrlOrReference = "http://images.cocodataset.org/val2017/000000039769.jpg")
  val imageBoxInfoPara1Ok = ImageBoxInfo(x = 11, y = 11, weight = 466, height = 310)
  val referencePara1Ng = Reference(url = "", surface = "pets", surfaceIndex = 3, isWholeSentence = false,
    originalUrlOrReference = "https://farm8.staticflickr.com/7287/8737869589_16ab5a83c4_z.jpg")
  val imageBoxInfoPara1Ng = ImageBoxInfo(x = 0, y = 0, weight = 630, height = 420)

  val paraphrase2 = "There is an animal"
  val referencePara2Ok = Reference(url = "", surface = "animal", surfaceIndex = 3, isWholeSentence = false,
    originalUrlOrReference = "http://images.cocodataset.org/train2017/000000428746.jpg")
  val imageBoxInfoPara2Ok = ImageBoxInfo(x = 77, y = 98, weight = 433, height = 222)
  val referencePara2Ng = Reference(url = "", surface = "animal", surfaceIndex = 3, isWholeSentence = false,
    originalUrlOrReference = "https://farm8.staticflickr.com/7287/8737869589_16ab5a83c4_z.jpg")
  val imageBoxInfoPara2Ng = ImageBoxInfo(x = 0, y = 0, weight = 630, height = 420)

  val paraphrase3 = "A large vehicle is parked."
  val referencePara3Ok = Reference(url = "", surface = "vehicle", surfaceIndex = 2, isWholeSentence = false,
    originalUrlOrReference = "https://farm8.staticflickr.com/7103/7210629614_5a388d9a9c_z.jpg")
  val imageBoxInfoPara3Ok = ImageBoxInfo(x = 23, y = 25, weight = 601, height = 341)
  val referencePara3Ng = Reference(url = "", surface = "vehicle", surfaceIndex = 2, isWholeSentence = false,
    originalUrlOrReference = "https://farm6.staticflickr.com/5195/7185346178_7e2664b081_z.jpg")
  val imageBoxInfoPara3Ng = ImageBoxInfo(x = 0, y = 0, weight = 640, height = 480)

  val paraphrase4 = "Two planes are flying."
  val referencePara4Ok = Reference(url = "", surface = "planes", surfaceIndex = 1, isWholeSentence = false,
    originalUrlOrReference = "https://farm2.staticflickr.com/1070/5110702674_350f5b367d_z.jpg")
  val imageBoxInfoPara4Ok = ImageBoxInfo(x = 223, y = 108, weight = 140, height = 205)
  val referencePara4Ng = Reference(url = "", surface = "planes", surfaceIndex = 1, isWholeSentence = false,
    originalUrlOrReference = "https://farm6.staticflickr.com/5177/5478834869_87a4ac58ec_z.jpg")
  val imageBoxInfoPara4Ng = ImageBoxInfo(x = 0, y = 0, weight = 640, height = 292)


  val lang = "en_US"

  //複数の主張(完全一致)
  "The specification1" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val sentenceId1 = getUUID()
      val sentenceId2 = getUUID()
      val knowledge1 = getKnowledge(lang=lang, sentence=sentence1, reference=reference1, imageBoxInfo=imageBoxInfo1, transversalState)
      val knowledge2 = getKnowledge(lang=lang, sentence=sentence2, reference=reference2, imageBoxInfo=imageBoxInfo2, transversalState)

      val paraphraseKnowledge1 = getKnowledge(lang=lang, sentence=paraphrase1, reference=referencePara1Ok, imageBoxInfo=imageBoxInfoPara1Ok, transversalState)
      val paraphraseKnowledge2 = getKnowledge(lang=lang, sentence=paraphrase2, reference=referencePara2Ok, imageBoxInfo=imageBoxInfoPara2Ok, transversalState)

      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1), transversalState)
      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId2, knowledge2), transversalState)
      val propositionIdForInference = getUUID()
      val sentenceIdForInference1 = getUUID()
      val sentenceIdForInference2 = getUUID() 
      val premiseKnowledge = List.empty[KnowledgeForParser]
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, sentenceIdForInference1, paraphraseKnowledge1), KnowledgeForParser(propositionIdForInference, sentenceIdForInference2, paraphraseKnowledge2))
      val inputSentence = Json.toJson(InputSentenceForParser(premiseKnowledge, claimKnowledge, ActionModeType.DEDUCTION_MODE.index)).toString()

      val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, getImageInfo2(List((referencePara1Ok, imageBoxInfoPara1Ok), (referencePara2Ok, imageBoxInfoPara2Ok)), transversalState), transversalState)
      //val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, getImageInfo2(List((referencePara1Ok, imageBoxInfoPara1Ok)), transversalState), transversalState)
      val updatedAsosJson = TestUtils.analyzeByBaseDeductionUnit(json, transversalState)
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(updatedAsosJson))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val verifyingEdgesList: List[VerifyingEdges] = Json.parse(jsonResult).as[List[VerifyingEdges]]

      assert(verifyingEdgesList.map(x => x.coveredPropositionEdges.size).sum == 4)
      TestUtils.checkMatchedBothSide(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=2)   
      TestUtils.checkMatchedOneSide(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=0)   
      TestUtils.checkNoMatch(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=0)   

      TestUtils.checkMatchedBothSide(json = json, sentenceId = sentenceIdForInference2, verifyingEdgesList=verifyingEdgesList, correctSize=2)   
      TestUtils.checkMatchedOneSide(json = json, sentenceId = sentenceIdForInference2, verifyingEdgesList=verifyingEdgesList, correctSize=0)   
      TestUtils.checkNoMatch(json = json, sentenceId = sentenceIdForInference2, verifyingEdgesList=verifyingEdgesList, correctSize=0)   

    }
  }

  //複数の主張(部分一致)
  "The specification2" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val sentenceId1 = getUUID()
      val sentenceId2 = getUUID()
      val knowledge1 = getKnowledge(lang=lang, sentence=sentence1, reference=reference1, imageBoxInfo=imageBoxInfo1, transversalState)
      val knowledge2 = getKnowledge(lang=lang, sentence=sentence2, reference=reference2, imageBoxInfo=imageBoxInfo2, transversalState)

      val paraphraseKnowledge1 = getKnowledge(lang=lang, sentence=paraphrase1, reference=referencePara1Ng, imageBoxInfo=imageBoxInfoPara1Ng, transversalState)
      val paraphraseKnowledge2 = getKnowledge(lang=lang, sentence=paraphrase2, reference=referencePara2Ng, imageBoxInfo=imageBoxInfoPara2Ng, transversalState)

      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1), transversalState)
      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId2, knowledge2), transversalState)
      val propositionIdForInference = getUUID()
      val sentenceIdForInference1 = getUUID()
      val sentenceIdForInference2 = getUUID() 
      val premiseKnowledge = List.empty[KnowledgeForParser]
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, sentenceIdForInference1, paraphraseKnowledge1), KnowledgeForParser(propositionIdForInference, sentenceIdForInference2, paraphraseKnowledge2))
      val inputSentence = Json.toJson(InputSentenceForParser(premiseKnowledge, claimKnowledge, ActionModeType.DEDUCTION_MODE.index)).toString()

      val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, getImageInfo2(List((referencePara1Ng, imageBoxInfoPara1Ng), (referencePara2Ng, imageBoxInfoPara2Ng)), transversalState), transversalState)
      //val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, getImageInfo2(List((referencePara1Ok, imageBoxInfoPara1Ok)), transversalState), transversalState)
      val updatedAsosJson = TestUtils.analyzeByBaseDeductionUnit(json, transversalState)
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(updatedAsosJson))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val verifyingEdgesList: List[VerifyingEdges] = Json.parse(jsonResult).as[List[VerifyingEdges]]

      assert(verifyingEdgesList.map(x => x.coveredPropositionEdges.size).sum == 4)
      TestUtils.checkMatchedBothSide(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=0)   
      TestUtils.checkMatchedOneSide(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=2)   
      TestUtils.checkNoMatch(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=0)   

      TestUtils.checkMatchedBothSide(json = json, sentenceId = sentenceIdForInference2, verifyingEdgesList=verifyingEdgesList, correctSize=0)   
      TestUtils.checkMatchedOneSide(json = json, sentenceId = sentenceIdForInference2, verifyingEdgesList=verifyingEdgesList, correctSize=2)   
      TestUtils.checkNoMatch(json = json, sentenceId = sentenceIdForInference2, verifyingEdgesList=verifyingEdgesList, correctSize=0)   

    }
  }  
  //一対の前提と主張(完全一致)
  "The specification3" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val sentenceId1 = getUUID()
      val sentenceId2 = getUUID()
      val knowledge1 = getKnowledge(lang=lang, sentence=sentence1, reference=reference1, imageBoxInfo=imageBoxInfo1, transversalState)
      val knowledge2 = getKnowledge(lang=lang, sentence=sentence2, reference=reference2, imageBoxInfo=imageBoxInfo2, transversalState)

      val paraphraseKnowledge1 = getKnowledge(lang=lang, sentence=paraphrase1, reference=referencePara1Ok, imageBoxInfo=imageBoxInfoPara1Ok, transversalState)
      val paraphraseKnowledge2 = getKnowledge(lang=lang, sentence=paraphrase2, reference=referencePara2Ok, imageBoxInfo=imageBoxInfoPara2Ok, transversalState)

      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1), transversalState)
      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId2, knowledge2), transversalState)
      val propositionIdForInference = getUUID()
      val sentenceIdForInference1 = getUUID()
      val sentenceIdForInference2 = getUUID() 
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, sentenceIdForInference1, paraphraseKnowledge1))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, sentenceIdForInference2, paraphraseKnowledge2))
      val inputSentence = Json.toJson(InputSentenceForParser(premiseKnowledge, claimKnowledge, ActionModeType.DEDUCTION_MODE.index)).toString()

      val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, getImageInfo2(List((referencePara1Ok, imageBoxInfoPara1Ok), (referencePara2Ok, imageBoxInfoPara2Ok)), transversalState), transversalState)
      //val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, getImageInfo2(List((referencePara1Ok, imageBoxInfoPara1Ok)), transversalState), transversalState)
      val updatedAsosJson = TestUtils.analyzeByBaseDeductionUnit(json, transversalState)
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(updatedAsosJson))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val verifyingEdgesList: List[VerifyingEdges] = Json.parse(jsonResult).as[List[VerifyingEdges]]

      assert(verifyingEdgesList.map(x => x.coveredPropositionEdges.size).sum == 4)
      TestUtils.checkMatchedBothSide(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=2)   
      TestUtils.checkMatchedOneSide(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=0)   
      TestUtils.checkNoMatch(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=0)   

      TestUtils.checkMatchedBothSide(json = json, sentenceId = sentenceIdForInference2, verifyingEdgesList=verifyingEdgesList, correctSize=2)   
      TestUtils.checkMatchedOneSide(json = json, sentenceId = sentenceIdForInference2, verifyingEdgesList=verifyingEdgesList, correctSize=0)   
      TestUtils.checkNoMatch(json = json, sentenceId = sentenceIdForInference2, verifyingEdgesList=verifyingEdgesList, correctSize=0)   

    }
  }

  //一対の前提と主張(部分一致)
  "The specification4" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val sentenceId1 = getUUID()
      val sentenceId2 = getUUID()
      val knowledge1 = getKnowledge(lang=lang, sentence=sentence1, reference=reference1, imageBoxInfo=imageBoxInfo1, transversalState)
      val knowledge2 = getKnowledge(lang=lang, sentence=sentence2, reference=reference2, imageBoxInfo=imageBoxInfo2, transversalState)

      val paraphraseKnowledge1 = getKnowledge(lang=lang, sentence=paraphrase1, reference=referencePara1Ng, imageBoxInfo=imageBoxInfoPara1Ng, transversalState)
      val paraphraseKnowledge2 = getKnowledge(lang=lang, sentence=paraphrase2, reference=referencePara2Ng, imageBoxInfo=imageBoxInfoPara2Ng, transversalState)

      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1), transversalState)
      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId2, knowledge2), transversalState)
      val propositionIdForInference = getUUID()
      val sentenceIdForInference1 = getUUID()
      val sentenceIdForInference2 = getUUID() 
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, sentenceIdForInference1, paraphraseKnowledge1))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, sentenceIdForInference2, paraphraseKnowledge2))
      val inputSentence = Json.toJson(InputSentenceForParser(premiseKnowledge, claimKnowledge, ActionModeType.DEDUCTION_MODE.index)).toString()

      val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, getImageInfo2(List((referencePara1Ng, imageBoxInfoPara1Ng), (referencePara2Ng, imageBoxInfoPara2Ng)), transversalState), transversalState)
      //val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, getImageInfo2(List((referencePara1Ok, imageBoxInfoPara1Ok)), transversalState), transversalState)
      val updatedAsosJson = TestUtils.analyzeByBaseDeductionUnit(json, transversalState)
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(updatedAsosJson))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val verifyingEdgesList: List[VerifyingEdges] = Json.parse(jsonResult).as[List[VerifyingEdges]]

      assert(verifyingEdgesList.map(x => x.coveredPropositionEdges.size).sum == 4)
      TestUtils.checkMatchedBothSide(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=0)   
      TestUtils.checkMatchedOneSide(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=2)   
      TestUtils.checkNoMatch(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=0)   

      TestUtils.checkMatchedBothSide(json = json, sentenceId = sentenceIdForInference2, verifyingEdgesList=verifyingEdgesList, correctSize=0)   
      TestUtils.checkMatchedOneSide(json = json, sentenceId = sentenceIdForInference2, verifyingEdgesList=verifyingEdgesList, correctSize=2)   
      TestUtils.checkNoMatch(json = json, sentenceId = sentenceIdForInference2, verifyingEdgesList=verifyingEdgesList, correctSize=0)   

    }
  }    
  //２対の前提と主張(完全一致)
  "The specification5" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val sentenceId1 = getUUID()
      val sentenceId2 = getUUID()
      val sentenceId3 = getUUID()
      val sentenceId4 = getUUID()

      val knowledge1 = getKnowledge(lang=lang, sentence=sentence1, reference=reference1, imageBoxInfo=imageBoxInfo1, transversalState)
      val knowledge2 = getKnowledge(lang=lang, sentence=sentence2, reference=reference2, imageBoxInfo=imageBoxInfo2, transversalState)
      val knowledge3 = getKnowledge(lang=lang, sentence=sentence3, reference=reference3, imageBoxInfo=imageBoxInfo3, transversalState)
      val knowledge4 = getKnowledge(lang=lang, sentence=sentence4, reference=reference4, imageBoxInfo=imageBoxInfo4, transversalState)

      val paraphraseKnowledge1 = getKnowledge(lang=lang, sentence=paraphrase1, reference=referencePara1Ok, imageBoxInfo=imageBoxInfoPara1Ok, transversalState)
      val paraphraseKnowledge2 = getKnowledge(lang=lang, sentence=paraphrase2, reference=referencePara2Ok, imageBoxInfo=imageBoxInfoPara2Ok, transversalState)
      val paraphraseKnowledge3 = getKnowledge(lang=lang, sentence=paraphrase3, reference=referencePara3Ok, imageBoxInfo=imageBoxInfoPara3Ok, transversalState)
      val paraphraseKnowledge4 = getKnowledge(lang=lang, sentence=paraphrase4, reference=referencePara4Ok, imageBoxInfo=imageBoxInfoPara4Ok, transversalState)

      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1), transversalState)
      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId2, knowledge2), transversalState)
      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId3, knowledge3), transversalState)
      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId4, knowledge4), transversalState)
      
      val propositionIdForInference = getUUID()
      val sentenceIdForInference1 = getUUID()
      val sentenceIdForInference2 = getUUID()
      val sentenceIdForInference3 = getUUID()
      val sentenceIdForInference4 = getUUID() 
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, sentenceIdForInference1, paraphraseKnowledge1), KnowledgeForParser(propositionIdForInference, sentenceIdForInference2, paraphraseKnowledge2))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, sentenceIdForInference3, paraphraseKnowledge3), KnowledgeForParser(propositionIdForInference, sentenceIdForInference4, paraphraseKnowledge4))
      val inputSentence = Json.toJson(InputSentenceForParser(premiseKnowledge, claimKnowledge, ActionModeType.DEDUCTION_MODE.index)).toString()

      val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, getImageInfo2(List((referencePara1Ok, imageBoxInfoPara1Ok), (referencePara2Ok, imageBoxInfoPara2Ok), (referencePara3Ok, imageBoxInfoPara3Ok), (referencePara4Ok, imageBoxInfoPara4Ok)), transversalState), transversalState)
      //val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, getImageInfo2(List((referencePara1Ok, imageBoxInfoPara1Ok)), transversalState), transversalState)
      val updatedAsosJson = TestUtils.analyzeByBaseDeductionUnit(json, transversalState)
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(updatedAsosJson))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val verifyingEdgesList: List[VerifyingEdges] = Json.parse(jsonResult).as[List[VerifyingEdges]]

      assert(verifyingEdgesList.map(x => x.coveredPropositionEdges.size).sum == 9)
      TestUtils.checkMatchedBothSide(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=2)   
      TestUtils.checkMatchedOneSide(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=0)   
      TestUtils.checkNoMatch(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=0)   

      TestUtils.checkMatchedBothSide(json = json, sentenceId = sentenceIdForInference2, verifyingEdgesList=verifyingEdgesList, correctSize=2)   
      TestUtils.checkMatchedOneSide(json = json, sentenceId = sentenceIdForInference2, verifyingEdgesList=verifyingEdgesList, correctSize=0)   
      TestUtils.checkNoMatch(json = json, sentenceId = sentenceIdForInference2, verifyingEdgesList=verifyingEdgesList, correctSize=0)   

      TestUtils.checkMatchedBothSide(json = json, sentenceId = sentenceIdForInference3, verifyingEdgesList=verifyingEdgesList, correctSize=3)   
      TestUtils.checkMatchedOneSide(json = json, sentenceId = sentenceIdForInference3, verifyingEdgesList=verifyingEdgesList, correctSize=0)   
      TestUtils.checkNoMatch(json = json, sentenceId = sentenceIdForInference3, verifyingEdgesList=verifyingEdgesList, correctSize=0)   

      TestUtils.checkMatchedBothSide(json = json, sentenceId = sentenceIdForInference4, verifyingEdgesList=verifyingEdgesList, correctSize=2)   
      TestUtils.checkMatchedOneSide(json = json, sentenceId = sentenceIdForInference4, verifyingEdgesList=verifyingEdgesList, correctSize=0)   
      TestUtils.checkNoMatch(json = json, sentenceId = sentenceIdForInference4, verifyingEdgesList=verifyingEdgesList, correctSize=0)   

    }
  }  
  //２対の前提と主張(部分一致)
  "The specification6" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val sentenceId1 = getUUID()
      val sentenceId2 = getUUID()
      val sentenceId3 = getUUID()
      val sentenceId4 = getUUID()

      val knowledge1 = getKnowledge(lang=lang, sentence=sentence1, reference=reference1, imageBoxInfo=imageBoxInfo1, transversalState)
      val knowledge2 = getKnowledge(lang=lang, sentence=sentence2, reference=reference2, imageBoxInfo=imageBoxInfo2, transversalState)
      val knowledge3 = getKnowledge(lang=lang, sentence=sentence3, reference=reference3, imageBoxInfo=imageBoxInfo3, transversalState)
      val knowledge4 = getKnowledge(lang=lang, sentence=sentence4, reference=reference4, imageBoxInfo=imageBoxInfo4, transversalState)

      val paraphraseKnowledge1 = getKnowledge(lang=lang, sentence=paraphrase1, reference=referencePara1Ng, imageBoxInfo=imageBoxInfoPara1Ng, transversalState)
      val paraphraseKnowledge2 = getKnowledge(lang=lang, sentence=paraphrase2, reference=referencePara2Ng, imageBoxInfo=imageBoxInfoPara2Ng, transversalState)
      val paraphraseKnowledge3 = getKnowledge(lang=lang, sentence=paraphrase3, reference=referencePara3Ng, imageBoxInfo=imageBoxInfoPara3Ng, transversalState)
      val paraphraseKnowledge4 = getKnowledge(lang=lang, sentence=paraphrase4, reference=referencePara4Ng, imageBoxInfo=imageBoxInfoPara4Ng, transversalState)

      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1), transversalState)
      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId2, knowledge2), transversalState)
      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId3, knowledge3), transversalState)
      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId4, knowledge4), transversalState)
      
      val propositionIdForInference = getUUID()
      val sentenceIdForInference1 = getUUID()
      val sentenceIdForInference2 = getUUID()
      val sentenceIdForInference3 = getUUID()
      val sentenceIdForInference4 = getUUID() 
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, sentenceIdForInference1, paraphraseKnowledge1), KnowledgeForParser(propositionIdForInference, sentenceIdForInference2, paraphraseKnowledge2))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, sentenceIdForInference3, paraphraseKnowledge3), KnowledgeForParser(propositionIdForInference, sentenceIdForInference4, paraphraseKnowledge4))
      val inputSentence = Json.toJson(InputSentenceForParser(premiseKnowledge, claimKnowledge, ActionModeType.DEDUCTION_MODE.index)).toString()

      val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, getImageInfo2(List((referencePara1Ng, imageBoxInfoPara1Ng), (referencePara2Ng, imageBoxInfoPara2Ng), (referencePara3Ng, imageBoxInfoPara3Ng), (referencePara4Ng, imageBoxInfoPara4Ng)), transversalState), transversalState)
      //val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, getImageInfo2(List((referencePara1Ok, imageBoxInfoPara1Ok)), transversalState), transversalState)
      val updatedAsosJson = TestUtils.analyzeByBaseDeductionUnit(json, transversalState)
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(updatedAsosJson))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val verifyingEdgesList: List[VerifyingEdges] = Json.parse(jsonResult).as[List[VerifyingEdges]]

      assert(verifyingEdgesList.map(x => x.coveredPropositionEdges.size).sum == 9)
      TestUtils.checkMatchedBothSide(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=0)   
      TestUtils.checkMatchedOneSide(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=2)   
      TestUtils.checkNoMatch(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=0)   

      TestUtils.checkMatchedBothSide(json = json, sentenceId = sentenceIdForInference2, verifyingEdgesList=verifyingEdgesList, correctSize=0)   
      TestUtils.checkMatchedOneSide(json = json, sentenceId = sentenceIdForInference2, verifyingEdgesList=verifyingEdgesList, correctSize=2)   
      TestUtils.checkNoMatch(json = json, sentenceId = sentenceIdForInference2, verifyingEdgesList=verifyingEdgesList, correctSize=0)   

      TestUtils.checkMatchedBothSide(json = json, sentenceId = sentenceIdForInference3, verifyingEdgesList=verifyingEdgesList, correctSize=0)   
      TestUtils.checkMatchedOneSide(json = json, sentenceId = sentenceIdForInference3, verifyingEdgesList=verifyingEdgesList, correctSize=3)   
      TestUtils.checkNoMatch(json = json, sentenceId = sentenceIdForInference3, verifyingEdgesList=verifyingEdgesList, correctSize=0)   

      TestUtils.checkMatchedBothSide(json = json, sentenceId = sentenceIdForInference4, verifyingEdgesList=verifyingEdgesList, correctSize=0)   
      TestUtils.checkMatchedOneSide(json = json, sentenceId = sentenceIdForInference4, verifyingEdgesList=verifyingEdgesList, correctSize=2)   
      TestUtils.checkNoMatch(json = json, sentenceId = sentenceIdForInference4, verifyingEdgesList=verifyingEdgesList, correctSize=0)   

    }
  } 
  /*
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
      val knowledge1 = getKnowledge(lang=lang, sentence=sentenceA, reference=referenceA, imageBoxInfo=imageBoxInfoA, transversalState)
      val knowledge2 = getKnowledge(lang=lang, sentence=sentenceB, reference=referenceB, imageBoxInfo=imageBoxInfoB, transversalState)
      val knowledge3 = getKnowledge(lang=lang, sentence=sentenceC, reference=referenceC, imageBoxInfo=imageBoxInfoC, transversalState)
      val knowledge4 = getKnowledge(lang=lang, sentence=sentenceD, reference=referenceD, imageBoxInfo=imageBoxInfoD, transversalState)

      val paraphrase1 = getKnowledge(lang=lang, sentence=paraphraseA, reference=referenceParaA, imageBoxInfo=imageBoxInfoParaA, transversalState)
      val paraphrase2 = getKnowledge(lang=lang, sentence=paraphraseB, reference=referenceParaB, imageBoxInfo=imageBoxInfoParaB, transversalState)
      val paraphrase3 = getKnowledge(lang=lang, sentence=paraphraseC, reference=referenceParaC, imageBoxInfo=imageBoxInfoParaC, transversalState)
      val paraphrase4 = getKnowledge(lang=lang, sentence=paraphraseD, reference=referenceParaD, imageBoxInfo=imageBoxInfoParaD, transversalState)

      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1), transversalState)
      registerSingleClaim(KnowledgeForParser(propositionId2, sentenceId2, knowledge3), transversalState)

      val knowledge1a = getKnowledge(lang=lang, sentence=sentenceA, reference=referenceA, imageBoxInfo=imageBoxInfoA, transversalState)
      val knowledge3a = getKnowledge(lang=lang, sentence=sentenceC, reference=referenceC, imageBoxInfo=imageBoxInfoC, transversalState)

      val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
        List(KnowledgeForParser(propositionId3, sentenceId3, knowledge1a), KnowledgeForParser(propositionId3, sentenceId4, knowledge2)),
        List(PropositionRelation("AND", 0,1)),
        List(KnowledgeForParser(propositionId3, sentenceId5, knowledge3a), KnowledgeForParser(propositionId3, sentenceId6, knowledge4)),
        List(PropositionRelation("AND", 0,1)))
      TestUtils.registerData(knowledgeSentenceSetForParser, transversalState)
      Thread.sleep(5000)

      val propositionIdForInference = getUUID()
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase1), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase2))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase3), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase4))
      val inputSentence = Json.toJson(InputSentenceForParser(premiseKnowledge, claimKnowledge, ActionModeType.DEDUCTION_MODE.index)).toString()
      val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, List(getImageInfo(referenceParaA, imageBoxInfoParaA, transversalState), getImageInfo(referenceParaB, imageBoxInfoParaB, transversalState), getImageInfo(referenceParaC, imageBoxInfoParaC, transversalState), getImageInfo(referenceParaD, imageBoxInfoParaD, transversalState)), transversalState)
      val updatedAsosJson = TestUtils.analyzeByBaseDeductionUnit(json, transversalState)
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(updatedAsosJson))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.PREMISE.index) && x.deductionResult.status).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index) && x.deductionResult.status).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index) && x.deductionResult.havePremiseInGivenProposition).size == 0)
    }
  }
  */
}
