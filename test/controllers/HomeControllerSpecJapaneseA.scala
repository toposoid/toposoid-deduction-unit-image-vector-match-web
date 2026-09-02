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
import controllers.TestUtilsEx.{getUUID, registerSingleClaim}
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
import com.ideal.linked.toposoid.knowledgebase.regist.model.ImageReference
import com.ideal.linked.toposoid.knowledgebase.regist.model.KnowledgeForImage
import com.ideal.linked.toposoid.test.utils.TestUtils.{uploadImage, getAnalyzedSentenceObjectsJson}
//import controllers.TestUtilsEx.getImageInfo2

class HomeControllerSpecJapaneseA extends PlaySpec with BeforeAndAfter with BeforeAndAfterAll with GuiceOneAppPerSuite with DefaultAwaitTimeout with Injecting {

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
  
  val sentence1 = "猫が２匹寝てます。"
  val reference1 = Reference(url = "", surface = "猫が", surfaceIndex = 0, isWholeSentence = false,
    originalUrlOrReference = "http://images.cocodataset.org/val2017/000000039769.jpg")
  val imageReference1 = ImageReference(reference1, x = 11, y = 11, width = 466, height = 310)
  val knowledgeForImage1 = KnowledgeForImage(getUUID(), imageReference1)        
  //val imageBoxInfo1 = ImageBoxInfo(x = 11, y = 11, weight = 466, height = 310)

  val sentence2 = "犬が１匹います。"
  val reference2 = Reference(url = "", surface = "犬が", surfaceIndex = 0, isWholeSentence = false,
    originalUrlOrReference = "http://images.cocodataset.org/train2017/000000428746.jpg")
  val imageReference2 = ImageReference(reference2, x = 77, y = 98, width = 433, height = 222)
  val knowledgeForImage2 = KnowledgeForImage(getUUID(), imageReference2)      
  //val imageBoxInfo2 = ImageBoxInfo(x = 77, y = 98, weight = 433, height = 222)

  val sentence3 = "トラックが一台止まっています。"
  val reference3 = Reference(url = "", surface = "トラックが", surfaceIndex = 0, isWholeSentence = false,
    originalUrlOrReference = "https://farm8.staticflickr.com/7103/7210629614_5a388d9a9c_z.jpg")
  val imageReference3 = ImageReference(reference3, x = 23, y = 25, width = 601, height = 341)
  val knowledgeForImage3 = KnowledgeForImage(getUUID(), imageReference3)      
  //val imageBoxInfo3 = ImageBoxInfo(x = 23, y = 25, weight = 601, height = 341)

  val sentence4 = "軍用機が2機飛んでいます。"  
  val reference4 = Reference(url = "", surface = "軍用機が", surfaceIndex = 0, isWholeSentence = false,  
    originalUrlOrReference = "https://farm2.staticflickr.com/1070/5110702674_350f5b367d_z.jpg")
  val imageReference4 = ImageReference(reference4, x = 223, y = 108, width = 140, height = 205)
  val knowledgeForImage4 = KnowledgeForImage(getUUID(), imageReference4)      
  //val imageBoxInfo4 = ImageBoxInfo(x = 223, y = 108, weight = 140, height = 205)

  val paraphrase1 = "ペットが２匹寝てます。"
  val referencePara1Ok = Reference(url = "", surface = "ペットが", surfaceIndex = 0, isWholeSentence = false,
    originalUrlOrReference = "http://images.cocodataset.org/val2017/000000039769.jpg")
  val imageReferencePara1Ok = ImageReference(referencePara1Ok, x = 11, y = 11, width = 466, height = 310)
  val knowledgeForImagePara1Ok = KnowledgeForImage(getUUID(), imageReferencePara1Ok)  
  //val imageBoxInfoPara1Ok = ImageBoxInfo(x = 11, y = 11, weight = 466, height = 310)
  val referencePara1Ng = Reference(url = "", surface = "ペットが", surfaceIndex = 0, isWholeSentence = false,
    originalUrlOrReference = "https://farm8.staticflickr.com/7287/8737869589_16ab5a83c4_z.jpg")
  val imageReferencePara1Ng = ImageReference(referencePara1Ng, x = 0, y = 0, width = 630, height = 420)
  val knowledgeForImagePara1Ng = KnowledgeForImage(getUUID(), imageReferencePara1Ng)      
  //val imageBoxInfoPara1Ng = ImageBoxInfo(x = 0, y = 0, weight = 630, height = 420)

  val paraphrase2 = "動物が１匹います。"
  val referencePara2Ok = Reference(url = "", surface = "動物が", surfaceIndex = 0, isWholeSentence = false,
    originalUrlOrReference = "http://images.cocodataset.org/train2017/000000428746.jpg")
  val imageReferencePara2Ok = ImageReference(referencePara2Ok, x = 77, y = 98, width = 433, height = 222)
  val knowledgeForImagePara2Ok = KnowledgeForImage(getUUID(), imageReferencePara2Ok)  
  //val imageBoxInfoPara2Ok = ImageBoxInfo(x = 77, y = 98, weight = 433, height = 222)
  val referencePara2Ng = Reference(url = "", surface = "動物が", surfaceIndex = 0, isWholeSentence = false,
    originalUrlOrReference = "https://farm8.staticflickr.com/7287/8737869589_16ab5a83c4_z.jpg")
  val imageReferencePara2Ng = ImageReference(referencePara2Ng, x = 0, y = 0, width = 630, height = 420)
  val knowledgeForImagePara2Ng = KnowledgeForImage(getUUID(), imageReferencePara2Ng)  
  //val imageBoxInfoPara2Ng = ImageBoxInfo(x = 0, y = 0, weight = 630, height = 420)

  val paraphrase3 = "トレーラーが一台止まっています。"
  val referencePara3Ok = Reference(url = "", surface = "トレーラーが", surfaceIndex = 0, isWholeSentence = false,
    originalUrlOrReference = "https://farm8.staticflickr.com/7103/7210629614_5a388d9a9c_z.jpg")
  val imageReferencePara3Ok = ImageReference(referencePara3Ok, x = 23, y = 25, width = 601, height = 341)
  val knowledgeForImagePara3Ok = KnowledgeForImage(getUUID(), imageReferencePara3Ok)    
  //val imageBoxInfoPara3Ok = ImageBoxInfo(x = 23, y = 25, weight = 601, height = 341)
  val referencePara3Ng = Reference(url = "", surface = "トレーラーが", surfaceIndex = 0, isWholeSentence = false,
    originalUrlOrReference = "https://farm6.staticflickr.com/5195/7185346178_7e2664b081_z.jpg")
  val imageReferencePara3Ng = ImageReference(referencePara3Ng, x = 0, y = 0, width = 640, height = 480)
  val knowledgeForImagePara3Ng = KnowledgeForImage(getUUID(), imageReferencePara3Ng)  
  //val imageBoxInfoPara3Ng = ImageBoxInfo(x = 0, y = 0, weight = 640, height = 480)

  val paraphrase4 = "飛行機が2機飛んでいます。"
  val referencePara4Ok = Reference(url = "", surface = "飛行機が", surfaceIndex = 0, isWholeSentence = false,
    originalUrlOrReference = "https://farm2.staticflickr.com/1070/5110702674_350f5b367d_z.jpg")
  val imageReferencePara4Ok = ImageReference(referencePara4Ok, x = 223, y = 108, width = 140, height = 205)
  val knowledgeForImagePara4Ok = KnowledgeForImage(getUUID(), imageReferencePara4Ok)    
  //val imageBoxInfoPara4Ok = ImageBoxInfo(x = 223, y = 108, weight = 140, height = 205)
  val referencePara4Ng = Reference(url = "", surface = "飛行機が", surfaceIndex = 0, isWholeSentence = false,
    originalUrlOrReference = "https://farm6.staticflickr.com/5177/5478834869_87a4ac58ec_z.jpg")
  val imageReferencePara4Ng = ImageReference(referencePara4Ng, x = 0, y = 0, width = 640, height = 292)
  val knowledgeForImagePara4Ng = KnowledgeForImage(getUUID(), imageReferencePara4Ng)  
  //val imageBoxInfoPara4Ng = ImageBoxInfo(x = 0, y = 0, weight = 640, height = 292)

  val lang = "ja_JP"
  
  //複数の主張(完全一致)
  "The specification1" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val sentenceId1 = getUUID()
      val sentenceId2 = getUUID()
      val knowledge1 = Knowledge(lang=lang, sentence=sentence1, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImage1, transversalState)))
      val knowledge2 = Knowledge(lang=lang, sentence=sentence2, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImage2, transversalState)))

      val paraphraseKnowledge1 = Knowledge(lang=lang, sentence=paraphrase1, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImagePara1Ok, transversalState)))
      val paraphraseKnowledge2 = Knowledge(lang=lang, sentence=paraphrase2, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImagePara2Ok, transversalState)))

      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1), transversalState)
      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId2, knowledge2), transversalState)
      val propositionIdForInference = getUUID()
      val sentenceIdForInference1 = getUUID()
      val sentenceIdForInference2 = getUUID() 
      val premiseKnowledge = List.empty[KnowledgeForParser]
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, sentenceIdForInference1, paraphraseKnowledge1), KnowledgeForParser(propositionIdForInference, sentenceIdForInference2, paraphraseKnowledge2))
      val inputSentenceForParser = InputSentenceForParser(premiseKnowledge, claimKnowledge, ActionModeType.DEDUCTION_MODE.index)
    
      val json = getAnalyzedSentenceObjectsJson(lang, inputSentenceForParser, transversalState)
      //val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, getImageInfo2(List((referencePara1Ok, imageBoxInfoPara1Ok), (referencePara2Ok, imageBoxInfoPara2Ok)), transversalState), transversalState)
      
      val updatedAsosJson = TestUtils.analyzeByBaseDeductionUnit(json, transversalState)
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(updatedAsosJson))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val verifyingEdgesList: List[VerifyingEdges] = Json.parse(jsonResult).as[List[VerifyingEdges]]

      assert(verifyingEdgesList.map(x => x.coveredPropositionEdges.size).sum == 2)
      TestUtils.checkMatchedBothSide(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=1)   
      TestUtils.checkMatchedOneSide(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=0)   
      TestUtils.checkNoMatch(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=0)   

      TestUtils.checkMatchedBothSide(json = json, sentenceId = sentenceIdForInference2, verifyingEdgesList=verifyingEdgesList, correctSize=1)   
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
      val knowledge1 = Knowledge(lang=lang, sentence=sentence1, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImage1, transversalState)))
      val knowledge2 = Knowledge(lang=lang, sentence=sentence2, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImage2, transversalState)))

      val paraphraseKnowledge1 = Knowledge(lang=lang, sentence=paraphrase1, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImagePara1Ng, transversalState)))
      val paraphraseKnowledge2 = Knowledge(lang=lang, sentence=paraphrase2, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImagePara2Ng, transversalState)))

      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1), transversalState)
      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId2, knowledge2), transversalState)
      val propositionIdForInference = getUUID()
      val sentenceIdForInference1 = getUUID()
      val sentenceIdForInference2 = getUUID() 
      val premiseKnowledge = List.empty[KnowledgeForParser]
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, sentenceIdForInference1, paraphraseKnowledge1), KnowledgeForParser(propositionIdForInference, sentenceIdForInference2, paraphraseKnowledge2))
      val inputSentenceForParser = InputSentenceForParser(premiseKnowledge, claimKnowledge, ActionModeType.DEDUCTION_MODE.index)

      val json = getAnalyzedSentenceObjectsJson(lang, inputSentenceForParser, transversalState)
      //val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, getImageInfo2(List((referencePara1Ng, imageBoxInfoPara1Ng), (referencePara2Ng, imageBoxInfoPara2Ng)), transversalState), transversalState)      
      val updatedAsosJson = TestUtils.analyzeByBaseDeductionUnit(json, transversalState)
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(updatedAsosJson))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val verifyingEdgesList: List[VerifyingEdges] = Json.parse(jsonResult).as[List[VerifyingEdges]]

      assert(verifyingEdgesList.map(x => x.coveredPropositionEdges.size).sum == 2)
      TestUtils.checkMatchedBothSide(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=0)   
      TestUtils.checkMatchedOneSide(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=1)   
      TestUtils.checkNoMatch(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=0)   

      TestUtils.checkMatchedBothSide(json = json, sentenceId = sentenceIdForInference2, verifyingEdgesList=verifyingEdgesList, correctSize=0)   
      TestUtils.checkMatchedOneSide(json = json, sentenceId = sentenceIdForInference2, verifyingEdgesList=verifyingEdgesList, correctSize=1)   
      TestUtils.checkNoMatch(json = json, sentenceId = sentenceIdForInference2, verifyingEdgesList=verifyingEdgesList, correctSize=0)   

    }
  }  
  
  //一対の前提と主張(完全一致)
  "The specification3" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val sentenceId1 = getUUID()
      val sentenceId2 = getUUID()
      val knowledge1 = Knowledge(lang=lang, sentence=sentence1, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImage1, transversalState)))
      val knowledge2 = Knowledge(lang=lang, sentence=sentence2, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImage2, transversalState)))

      val paraphraseKnowledge1 = Knowledge(lang=lang, sentence=paraphrase1, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImagePara1Ok, transversalState)))
      val paraphraseKnowledge2 = Knowledge(lang=lang, sentence=paraphrase2, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImagePara2Ok, transversalState)))

      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1), transversalState)
      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId2, knowledge2), transversalState)
      val propositionIdForInference = getUUID()
      val sentenceIdForInference1 = getUUID()
      val sentenceIdForInference2 = getUUID() 
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, sentenceIdForInference1, paraphraseKnowledge1))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, sentenceIdForInference2, paraphraseKnowledge2))
      val inputSentenceForParser = InputSentenceForParser(premiseKnowledge, claimKnowledge, ActionModeType.DEDUCTION_MODE.index)

      val json = getAnalyzedSentenceObjectsJson(lang, inputSentenceForParser, transversalState)
      //val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, getImageInfo2(List((referencePara1Ok, imageBoxInfoPara1Ok), (referencePara2Ok, imageBoxInfoPara2Ok)), transversalState), transversalState)      
      val updatedAsosJson = TestUtils.analyzeByBaseDeductionUnit(json, transversalState)
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(updatedAsosJson))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val verifyingEdgesList: List[VerifyingEdges] = Json.parse(jsonResult).as[List[VerifyingEdges]]

      assert(verifyingEdgesList.map(x => x.coveredPropositionEdges.size).sum == 2)
      TestUtils.checkMatchedBothSide(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=1)   
      TestUtils.checkMatchedOneSide(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=0)   
      TestUtils.checkNoMatch(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=0)   

      TestUtils.checkMatchedBothSide(json = json, sentenceId = sentenceIdForInference2, verifyingEdgesList=verifyingEdgesList, correctSize=1)   
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
      val knowledge1 = Knowledge(lang=lang, sentence=sentence1, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImage1, transversalState)))
      val knowledge2 = Knowledge(lang=lang, sentence=sentence2, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImage2, transversalState)))

      val paraphraseKnowledge1 = Knowledge(lang=lang, sentence=paraphrase1, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImagePara1Ng, transversalState)))
      val paraphraseKnowledge2 = Knowledge(lang=lang, sentence=paraphrase2, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImagePara2Ng, transversalState)))

      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1), transversalState)
      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId2, knowledge2), transversalState)
      val propositionIdForInference = getUUID()
      val sentenceIdForInference1 = getUUID()
      val sentenceIdForInference2 = getUUID() 
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, sentenceIdForInference1, paraphraseKnowledge1))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, sentenceIdForInference2, paraphraseKnowledge2))
      val inputSentenceForParser = InputSentenceForParser(premiseKnowledge, claimKnowledge, ActionModeType.DEDUCTION_MODE.index)

      val json = getAnalyzedSentenceObjectsJson(lang, inputSentenceForParser, transversalState)
      //val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, getImageInfo2(List((referencePara1Ng, imageBoxInfoPara1Ng), (referencePara2Ng, imageBoxInfoPara2Ng)), transversalState), transversalState)      
      val updatedAsosJson = TestUtils.analyzeByBaseDeductionUnit(json, transversalState)
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(updatedAsosJson))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val verifyingEdgesList: List[VerifyingEdges] = Json.parse(jsonResult).as[List[VerifyingEdges]]

      assert(verifyingEdgesList.map(x => x.coveredPropositionEdges.size).sum == 2)
      TestUtils.checkMatchedBothSide(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=0)   
      TestUtils.checkMatchedOneSide(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=1)   
      TestUtils.checkNoMatch(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=0)   

      TestUtils.checkMatchedBothSide(json = json, sentenceId = sentenceIdForInference2, verifyingEdgesList=verifyingEdgesList, correctSize=0)   
      TestUtils.checkMatchedOneSide(json = json, sentenceId = sentenceIdForInference2, verifyingEdgesList=verifyingEdgesList, correctSize=1)   
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

      val knowledge1 = Knowledge(lang=lang, sentence=sentence1, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImage1, transversalState)))
      val knowledge2 = Knowledge(lang=lang, sentence=sentence2, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImage2, transversalState)))
      val knowledge3 = Knowledge(lang=lang, sentence=sentence3, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImage3, transversalState)))
      val knowledge4 = Knowledge(lang=lang, sentence=sentence4, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImage4, transversalState)))

      val paraphraseKnowledge1 = Knowledge(lang=lang, sentence=paraphrase1, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImagePara1Ok, transversalState)))
      val paraphraseKnowledge2 = Knowledge(lang=lang, sentence=paraphrase2, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImagePara2Ok, transversalState)))
      val paraphraseKnowledge3 = Knowledge(lang=lang, sentence=paraphrase3, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImagePara3Ok, transversalState)))
      val paraphraseKnowledge4 = Knowledge(lang=lang, sentence=paraphrase4, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImagePara4Ok, transversalState)))

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
      val inputSentenceForParser = InputSentenceForParser(premiseKnowledge, claimKnowledge, ActionModeType.DEDUCTION_MODE.index)

      val json = getAnalyzedSentenceObjectsJson(lang, inputSentenceForParser, transversalState)
      //val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, getImageInfo2(List((referencePara1Ok, imageBoxInfoPara1Ok), (referencePara2Ok, imageBoxInfoPara2Ok), (referencePara3Ok, imageBoxInfoPara3Ok), (referencePara4Ok, imageBoxInfoPara4Ok)), transversalState), transversalState)      
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
      TestUtils.checkMatchedBothSide(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=1)   
      TestUtils.checkMatchedOneSide(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=0)   
      TestUtils.checkNoMatch(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=0)   

      TestUtils.checkMatchedBothSide(json = json, sentenceId = sentenceIdForInference2, verifyingEdgesList=verifyingEdgesList, correctSize=1)   
      TestUtils.checkMatchedOneSide(json = json, sentenceId = sentenceIdForInference2, verifyingEdgesList=verifyingEdgesList, correctSize=0)   
      TestUtils.checkNoMatch(json = json, sentenceId = sentenceIdForInference2, verifyingEdgesList=verifyingEdgesList, correctSize=0)   

      TestUtils.checkMatchedBothSide(json = json, sentenceId = sentenceIdForInference3, verifyingEdgesList=verifyingEdgesList, correctSize=1)   
      TestUtils.checkMatchedOneSide(json = json, sentenceId = sentenceIdForInference3, verifyingEdgesList=verifyingEdgesList, correctSize=0)   
      TestUtils.checkNoMatch(json = json, sentenceId = sentenceIdForInference3, verifyingEdgesList=verifyingEdgesList, correctSize=0)   

      TestUtils.checkMatchedBothSide(json = json, sentenceId = sentenceIdForInference4, verifyingEdgesList=verifyingEdgesList, correctSize=1)   
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

      val knowledge1 = Knowledge(lang=lang, sentence=sentence1, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImage1, transversalState)))
      val knowledge2 = Knowledge(lang=lang, sentence=sentence2, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImage2, transversalState)))
      val knowledge3 = Knowledge(lang=lang, sentence=sentence3, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImage3, transversalState)))
      val knowledge4 = Knowledge(lang=lang, sentence=sentence4, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImage4, transversalState)))

      val paraphraseKnowledge1 = Knowledge(lang=lang, sentence=paraphrase1, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImagePara1Ng, transversalState)))
      val paraphraseKnowledge2 = Knowledge(lang=lang, sentence=paraphrase2, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImagePara2Ng, transversalState)))
      val paraphraseKnowledge3 = Knowledge(lang=lang, sentence=paraphrase3, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImagePara3Ng, transversalState)))
      val paraphraseKnowledge4 = Knowledge(lang=lang, sentence=paraphrase4, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImagePara4Ng, transversalState)))

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
      val inputSentenceForParser = InputSentenceForParser(premiseKnowledge, claimKnowledge, ActionModeType.DEDUCTION_MODE.index)

      val json = getAnalyzedSentenceObjectsJson(lang, inputSentenceForParser, transversalState)
      //val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, getImageInfo2(List((referencePara1Ng, imageBoxInfoPara1Ng), (referencePara2Ng, imageBoxInfoPara2Ng), (referencePara3Ng, imageBoxInfoPara3Ng), (referencePara4Ng, imageBoxInfoPara4Ng)), transversalState), transversalState)      
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
      TestUtils.checkMatchedOneSide(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=1)   
      TestUtils.checkNoMatch(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=0)   

      TestUtils.checkMatchedBothSide(json = json, sentenceId = sentenceIdForInference2, verifyingEdgesList=verifyingEdgesList, correctSize=0)   
      TestUtils.checkMatchedOneSide(json = json, sentenceId = sentenceIdForInference2, verifyingEdgesList=verifyingEdgesList, correctSize=1)   
      TestUtils.checkNoMatch(json = json, sentenceId = sentenceIdForInference2, verifyingEdgesList=verifyingEdgesList, correctSize=0)   

      TestUtils.checkMatchedBothSide(json = json, sentenceId = sentenceIdForInference3, verifyingEdgesList=verifyingEdgesList, correctSize=0)   
      TestUtils.checkMatchedOneSide(json = json, sentenceId = sentenceIdForInference3, verifyingEdgesList=verifyingEdgesList, correctSize=1)   
      TestUtils.checkNoMatch(json = json, sentenceId = sentenceIdForInference3, verifyingEdgesList=verifyingEdgesList, correctSize=0)   

      TestUtils.checkMatchedBothSide(json = json, sentenceId = sentenceIdForInference4, verifyingEdgesList=verifyingEdgesList, correctSize=0)   
      TestUtils.checkMatchedOneSide(json = json, sentenceId = sentenceIdForInference4, verifyingEdgesList=verifyingEdgesList, correctSize=1)   
      TestUtils.checkNoMatch(json = json, sentenceId = sentenceIdForInference4, verifyingEdgesList=verifyingEdgesList, correctSize=0)   

    }
  } 
  
  
}
