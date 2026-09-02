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

class HomeControllerSpecEnglish extends PlaySpec with BeforeAndAfter with BeforeAndAfterAll with GuiceOneAppPerSuite with DefaultAwaitTimeout with Injecting {

  val transversalState:TransversalState = TransversalState(userId="test-user", username="guest", roleId=0, csrfToken = "")
  val transversalStateJson:String = Json.toJson(transversalState).toString()

  before {
    TestUtilsEx.deleteNeo4JAllData(transversalState)
    //ToposoidUtils.callComponent("{}", conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_PORT"), "createSchema", transversalState)
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
  val imageReference1 = ImageReference(reference1, x = 11, y = 11, width = 466, height = 310)
  val knowledgeForImage1 = KnowledgeForImage(getUUID(), imageReference1)      
  //val imageBoxInfo1 = ImageBoxInfo(x =11 , y = 11, weight = 466, height = 310)

  val paraphrase1 = "There are two pets."
  val referencePara1Ok = Reference(url = "", surface = "pets", surfaceIndex = 3, isWholeSentence = false,
    originalUrlOrReference = "http://images.cocodataset.org/val2017/000000039769.jpg")
  val imageReferencePara1Ok = ImageReference(referencePara1Ok, x = 11, y = 11, width = 466, height = 310)
  val knowledgeForImagePara1Ok = KnowledgeForImage(getUUID(), imageReferencePara1Ok)    
  //val imageBoxInfoPara1Ok = ImageBoxInfo(x =11 , y = 11, weight = 466, height = 310)

  val referencePara1Ng = Reference(url = "", surface = "pets", surfaceIndex = 3, isWholeSentence = false,
    originalUrlOrReference = "http://images.cocodataset.org/train2017/000000428746.jpg")
  val imageReferencePara1Ng = ImageReference(referencePara1Ng, x = 77, y = 98, width = 433, height = 222)
  val knowledgeForImagePara1Ng = KnowledgeForImage(getUUID(), imageReferencePara1Ng)    
  //val imageBoxInfoPara1Ng = ImageBoxInfo(x = 77, y = 98, weight = 433, height = 222)


  val sentence2 = "There is a cat and a dog."
  //val sentence2 = "There is a pet and an animal."
  val reference2a = Reference(url = "", surface = "cat", surfaceIndex = 3, isWholeSentence = false,
    originalUrlOrReference = "http://images.cocodataset.org/val2017/000000039769.jpg")
  val imageReference2a = ImageReference(reference2a, x = 11, y = 11, width = 466, height = 310)
  val knowledgeForImage2a = KnowledgeForImage(getUUID(), imageReference2a)      
  //val imageBoxInfo2a = ImageBoxInfo(x =11 , y = 11, weight = 466, height = 310)
  val reference2b = Reference(url = "", surface = "dog", surfaceIndex = 6, isWholeSentence = false,
    originalUrlOrReference = "http://images.cocodataset.org/train2017/000000428746.jpg")
  val imageReference2b = ImageReference(reference2b, x = 77, y = 98, width = 433, height = 222)  
  val knowledgeForImage2b = KnowledgeForImage(getUUID(), imageReference2b)       
  //val imageBoxInfo2b = ImageBoxInfo(x = 77, y = 98, weight = 433, height = 222)

  val paraphrase2 = "There is a pet and an animal."
  val referencePara2aOk = Reference(url = "", surface = "pet", surfaceIndex = 3, isWholeSentence = false,
    originalUrlOrReference = "http://images.cocodataset.org/val2017/000000039769.jpg")
  val imageReferencePara2aOk = ImageReference(referencePara2aOk, x = 11, y = 11, width = 466, height = 310)
  val knowledgeForImagePara2aOk = KnowledgeForImage(getUUID(), imageReferencePara2aOk)  
  //val imageBoxInfoPara2aOk = ImageBoxInfo(x =11 , y = 11, weight = 466, height = 310)
  val referencePara2bOk = Reference(url = "", surface = "animal", surfaceIndex = 6, isWholeSentence = false,
    originalUrlOrReference = "http://images.cocodataset.org/train2017/000000428746.jpg")
  val imageReference2bOk = ImageReference(referencePara2bOk, x = 77, y = 98, width = 433, height = 222)  
  val knowledgeForImagePara2bOk = KnowledgeForImage(getUUID(), imageReference2bOk)       
  //val imageBoxInfoPara2bOk = ImageBoxInfo(x = 77, y = 98, weight = 433, height = 222)
  val referencePara2aNg = Reference(url = "", surface = "pet", surfaceIndex = 3, isWholeSentence = false,
    originalUrlOrReference = "https://farm8.staticflickr.com/7287/8737869589_16ab5a83c4_z.jpg")
  val imageReferencePara2aNg = ImageReference(referencePara2aNg, x = 0, y = 0, width = 630, height = 420)
  val knowledgeForImagePara2aNg = KnowledgeForImage(getUUID(), imageReferencePara2aNg)      
  //val imageBoxInfoPara2aNg = ImageBoxInfo(x = 0, y = 0, weight = 630, height = 420)
  val referencePara2bNg = Reference(url = "", surface = "animal", surfaceIndex = 6, isWholeSentence = false,
    originalUrlOrReference = "https://farm8.staticflickr.com/7287/8737869589_16ab5a83c4_z.jpg")
  val imageReferencePara2bNg = ImageReference(referencePara2bNg, x = 0, y = 0, width = 630, height = 420)
  val knowledgeForImagePara2bNg = KnowledgeForImage(getUUID(), imageReferencePara2bNg)      
  //val imageBoxInfoPara2bNg = ImageBoxInfo(x =0 , y = 0, weight = 630, height = 420)


  val sentence3 = "Cats are not dogs."
  val reference3a = Reference(url = "", surface = "Cats", surfaceIndex = 0, isWholeSentence = false,
    originalUrlOrReference = "http://images.cocodataset.org/val2017/000000039769.jpg")
  val imageReference3a = ImageReference(reference3a, x = 11, y = 11, width = 466, height = 310)
  val knowledgeForImage3a = KnowledgeForImage(getUUID(), imageReference3a)    
  //val imageBoxInfo3a = ImageBoxInfo(x =11 , y = 11, weight = 466, height = 310)
  val reference3b = Reference(url = "", surface = "dogs", surfaceIndex = 3, isWholeSentence = false,
    originalUrlOrReference = "http://images.cocodataset.org/train2017/000000428746.jpg")
  val imageReference3b = ImageReference(reference3b, x = 77, y = 98, width = 433, height = 222)
  val knowledgeForImage3b = KnowledgeForImage(getUUID(), imageReference3b)    
  //val imageBoxInfo3b = ImageBoxInfo(x = 77, y = 98, weight = 433, height = 222)

  val paraphrase3 = "Pets are not friends."
  val referencePara3aOk = Reference(url = "", surface = "Pets", surfaceIndex = 0, isWholeSentence = false,
    originalUrlOrReference = "http://images.cocodataset.org/val2017/000000039769.jpg")
  val imageReferencePara3aOk = ImageReference(referencePara3aOk, x = 11, y = 11, width = 466, height = 310)
  val knowledgeForImagePara3aOk = KnowledgeForImage(getUUID(), imageReferencePara3aOk)    
  //val imageBoxInfoPara3aOk = ImageBoxInfo(x =11 , y = 11, weight = 466, height = 310)
  val referencePara3bOk = Reference(url = "", surface = "friends", surfaceIndex = 3, isWholeSentence = false,
    originalUrlOrReference = "http://images.cocodataset.org/train2017/000000428746.jpg")
  val imageReferencePara3bOk = ImageReference(referencePara3bOk, x = 77, y = 98, width = 433, height = 222)
  val knowledgeForImagePara3bOk = KnowledgeForImage(getUUID(), imageReferencePara3bOk)    
  //val imageBoxInfoPara3bOk = ImageBoxInfo(x = 77, y = 98, weight = 433, height = 222)

  val paraphrase4 = "Pets are friends."
  val referencePara4aOk = Reference(url = "", surface = "Pets", surfaceIndex = 0, isWholeSentence = false,
    originalUrlOrReference = "http://images.cocodataset.org/val2017/000000039769.jpg")
  val imageReferencePara4aOk = ImageReference(referencePara4aOk, x = 11, y = 11, width = 466, height = 310)
  val knowledgeForImagePara4aOk = KnowledgeForImage(getUUID(), imageReferencePara4aOk)    
  //val imageBoxInfoPara4aOk = ImageBoxInfo(x =11 , y = 11, weight = 466, height = 310)
  val referencePara4bOk = Reference(url = "", surface = "friends", surfaceIndex = 3, isWholeSentence = false,
    originalUrlOrReference = "http://images.cocodataset.org/train2017/000000428746.jpg")
  val imageReferencePara4bOk = ImageReference(referencePara4bOk, x = 77, y = 98, width = 433, height = 222)
  val knowledgeForImagePara4bOk = KnowledgeForImage(getUUID(), imageReferencePara4bOk)      
  //val imageBoxInfoPara4bOk = ImageBoxInfo(x = 77, y = 98, weight = 433, height = 222)
  
  val lang = "en_US"
  //片側対象、片側一致
  "The specification1" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val sentenceId1 = getUUID()
      val knowledge1 = Knowledge(lang=lang, sentence=sentence1, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImage1, transversalState)))
      val paraphraseKnowledge1 = Knowledge(lang=lang, sentence=paraphrase1, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImagePara1Ok, transversalState)))
      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1), transversalState)
      val propositionIdForInference1 = getUUID()
      val sentenceIdForInference1 = getUUID()
      val premiseKnowledge = List.empty[KnowledgeForParser]
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference1, sentenceIdForInference1, paraphraseKnowledge1))
      val inputSentenceForParser = InputSentenceForParser(premiseKnowledge, claimKnowledge, ActionModeType.DEDUCTION_MODE.index)
      val json = getAnalyzedSentenceObjectsJson(lang, inputSentenceForParser, transversalState)
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
      assert(verifyingEdgesList.map(x => x.coveredPropositionEdges.size).sum == 2)

      TestUtils.checkMatchedBothSide(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=2)   
      TestUtils.checkMatchedOneSide(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=0)   
      TestUtils.checkNoMatch(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=0)   
    }
  }
  
  //片側対象、片側不一致
  "The specification2" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val sentenceId1 = getUUID()
      //val knowledge1 = Knowledge(sentenceA,"ja_JP", "{}", false, List(imageA))
      val knowledge1 = Knowledge(lang=lang, sentence=sentence1, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImage1, transversalState)))
      val paraphraseKnowledge1 = Knowledge(lang=lang, sentence=paraphrase1, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImagePara1Ng, transversalState)))
      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1), transversalState)
      val propositionIdForInference1 = getUUID()
      val sentenceIdForInference1 = getUUID()
      val premiseKnowledge = List.empty[KnowledgeForParser]
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference1, sentenceIdForInference1, paraphraseKnowledge1))
      val inputSentenceForParser = InputSentenceForParser(premiseKnowledge, claimKnowledge, ActionModeType.DEDUCTION_MODE.index)
      val json = getAnalyzedSentenceObjectsJson(lang, inputSentenceForParser, transversalState)
      //val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, getImageInfo2(List((referencePara1Ng, imageBoxInfoPara1Ng)), transversalState), transversalState)
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
      TestUtils.checkMatchedOneSide(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=2)   
      TestUtils.checkNoMatch(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=0)   
    }
  }

  //両側対象、両側一致
  "The specification3" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val sentenceId1 = getUUID()
      //val knowledge1 = Knowledge(sentenceA,"ja_JP", "{}", false, List(imageA))
      val knowledge1 = Knowledge(lang=lang, sentence=sentence2, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImage2a, transversalState), uploadImage(knowledgeForImage2b, transversalState)))
      val paraphraseKnowledge1 = Knowledge(lang=lang, sentence=paraphrase2, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImagePara2aOk, transversalState), uploadImage(knowledgeForImagePara2bOk, transversalState)))
      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1), transversalState)
      
      val propositionIdForInference1 = getUUID()
      val sentenceIdForInference1 = getUUID()
      val premiseKnowledge = List.empty[KnowledgeForParser]
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference1, sentenceIdForInference1, paraphraseKnowledge1))
      val inputSentenceForParser = InputSentenceForParser(premiseKnowledge, claimKnowledge, ActionModeType.DEDUCTION_MODE.index)
      val json = getAnalyzedSentenceObjectsJson(lang, inputSentenceForParser, transversalState)
      //val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, getImageInfo2(List((referencePara2aOk, imageBoxInfoPara2aOk), (referencePara2bOk, imageBoxInfoPara2bOk)), transversalState), transversalState)
      val updatedAsosJson = TestUtils.analyzeByBaseDeductionUnit(json, transversalState)
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(updatedAsosJson))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val verifyingEdgesList: List[VerifyingEdges] = Json.parse(jsonResult).as[List[VerifyingEdges]]
      assert(verifyingEdgesList.map(x => x.coveredPropositionEdges.size).sum == 5)

      TestUtils.checkMatchedBothSide(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=5)   
      TestUtils.checkMatchedOneSide(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=0)   
      TestUtils.checkNoMatch(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=0)  
      
    }
  }
  
  //両側対象、片側のみ一致その１
  "The specification4a" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val sentenceId1 = getUUID()
      //val knowledge1 = Knowledge(sentenceA,"ja_JP", "{}", false, List(imageA))
      val knowledge1 = Knowledge(lang=lang, sentence=sentence2, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImage2a, transversalState), uploadImage(knowledgeForImage2b, transversalState)))
      val paraphraseKnowledge1 = Knowledge(lang=lang, sentence=paraphrase2, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImagePara2aNg, transversalState), uploadImage(knowledgeForImagePara2bOk, transversalState)))
      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1), transversalState)
      
      val propositionIdForInference1 = getUUID()
      val sentenceIdForInference1 = getUUID()
      val premiseKnowledge = List.empty[KnowledgeForParser]
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference1, sentenceIdForInference1, paraphraseKnowledge1))
      val inputSentenceForParser = InputSentenceForParser(premiseKnowledge, claimKnowledge, ActionModeType.DEDUCTION_MODE.index)
      val json = getAnalyzedSentenceObjectsJson(lang, inputSentenceForParser, transversalState)
      //val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, getImageInfo2(List((referencePara2aNg, imageBoxInfoPara2aNg), (referencePara2bOk, imageBoxInfoPara2bOk)), transversalState), transversalState)
      val updatedAsosJson = TestUtils.analyzeByBaseDeductionUnit(json, transversalState)
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(updatedAsosJson))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val verifyingEdgesList: List[VerifyingEdges] = Json.parse(jsonResult).as[List[VerifyingEdges]]
      assert(verifyingEdgesList.map(x => x.coveredPropositionEdges.size).sum == 5)

      TestUtils.checkMatchedBothSide(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=1)   
      TestUtils.checkMatchedOneSide(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=4)   
      TestUtils.checkNoMatch(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=0)  
      
    }
  }

  //両側対象、片側のみ一致その2
  "The specification4b" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val sentenceId1 = getUUID()
      //val knowledge1 = Knowledge(sentenceA,"ja_JP", "{}", false, List(imageA))
      val knowledge1 = Knowledge(lang=lang, sentence=sentence2, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImage2a, transversalState), uploadImage(knowledgeForImage2b, transversalState)))
      val paraphraseKnowledge1 = Knowledge(lang=lang, sentence=paraphrase2, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImagePara2aOk, transversalState), uploadImage(knowledgeForImagePara2bNg, transversalState)))
      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1), transversalState)
      
      val propositionIdForInference1 = getUUID()
      val sentenceIdForInference1 = getUUID()
      val premiseKnowledge = List.empty[KnowledgeForParser]
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference1, sentenceIdForInference1, paraphraseKnowledge1))
      val inputSentenceForParser = InputSentenceForParser(premiseKnowledge, claimKnowledge, ActionModeType.DEDUCTION_MODE.index)
      val json = getAnalyzedSentenceObjectsJson(lang, inputSentenceForParser, transversalState)
      //val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, getImageInfo2(List((referencePara2aOk, imageBoxInfoPara2aOk), (referencePara2bNg, imageBoxInfoPara2bNg)), transversalState), transversalState)
      val updatedAsosJson = TestUtils.analyzeByBaseDeductionUnit(json, transversalState)
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(updatedAsosJson))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val verifyingEdgesList: List[VerifyingEdges] = Json.parse(jsonResult).as[List[VerifyingEdges]]
      assert(verifyingEdgesList.map(x => x.coveredPropositionEdges.size).sum == 5)

      TestUtils.checkMatchedBothSide(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=3)   
      TestUtils.checkMatchedOneSide(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=2)   
      TestUtils.checkNoMatch(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=0)  
      
    }
  }

  //両側対象、否定一致
  "The specification5" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val sentenceId1 = getUUID()
      //val knowledge1 = Knowledge(sentenceA,"ja_JP", "{}", false, List(imageA))
      val knowledge1 = Knowledge(lang=lang, sentence=sentence3, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImage3a, transversalState), uploadImage(knowledgeForImage3b, transversalState)))
      val paraphraseKnowledge1 = Knowledge(lang=lang, sentence=paraphrase3, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImagePara3aOk, transversalState), uploadImage(knowledgeForImagePara3bOk, transversalState)))
      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1), transversalState)
      
      val propositionIdForInference1 = getUUID()
      val sentenceIdForInference1 = getUUID()
      val premiseKnowledge = List.empty[KnowledgeForParser]
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference1, sentenceIdForInference1, paraphraseKnowledge1))
      val inputSentenceForParser = InputSentenceForParser(premiseKnowledge, claimKnowledge, ActionModeType.DEDUCTION_MODE.index)
      val json = getAnalyzedSentenceObjectsJson(lang, inputSentenceForParser, transversalState)
      //val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, getImageInfo2(List((referencePara3aOk, imageBoxInfoPara3aOk), (referencePara3bOk, imageBoxInfoPara3bOk)), transversalState), transversalState)
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

      TestUtils.checkMatchedBothSide(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=2)   
      TestUtils.checkMatchedOneSide(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=0)   
      TestUtils.checkNoMatch(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=0)  
      
    }
  }

  //両側対象、否定不一致
  "The specification6" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val sentenceId1 = getUUID()
      //val knowledge1 = Knowledge(sentenceA,"ja_JP", "{}", false, List(imageA))
      val knowledge1 = Knowledge(lang=lang, sentence=sentence3, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImage3a, transversalState), uploadImage(knowledgeForImage2b, transversalState)))
      val paraphraseKnowledge1 = Knowledge(lang=lang, sentence=paraphrase4, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImagePara4aOk, transversalState), uploadImage(knowledgeForImagePara4bOk, transversalState)))
      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1), transversalState)
      
      val propositionIdForInference1 = getUUID()
      val sentenceIdForInference1 = getUUID()
      val premiseKnowledge = List.empty[KnowledgeForParser]
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference1, sentenceIdForInference1, paraphraseKnowledge1))
      val inputSentenceForParser = InputSentenceForParser(premiseKnowledge, claimKnowledge, ActionModeType.DEDUCTION_MODE.index)
      val json = getAnalyzedSentenceObjectsJson(lang, inputSentenceForParser, transversalState)
      //val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, getImageInfo2(List((referencePara4aOk, imageBoxInfoPara4aOk), (referencePara4bOk, imageBoxInfoPara4bOk)), transversalState), transversalState)
      val updatedAsosJson = TestUtils.analyzeByBaseDeductionUnit(json, transversalState)
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(updatedAsosJson))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val verifyingEdgesList: List[VerifyingEdges] = Json.parse(jsonResult).as[List[VerifyingEdges]]
      assert(verifyingEdgesList.map(x => x.coveredPropositionEdges.size).sum == 0)

      TestUtils.checkMatchedBothSide(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=0)   
      TestUtils.checkMatchedOneSide(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=0)   
      TestUtils.checkNoMatch(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=0)  
      
    }
  }  
  //全て被覆できないケース
  "The specification7" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val sentenceId1 = getUUID()
      //val knowledge1 = Knowledge(sentenceA,"ja_JP", "{}", false, List(imageA))
      val knowledge1 = Knowledge(lang=lang, sentence=sentence2, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImage2a, transversalState), uploadImage(knowledgeForImage2b, transversalState)))
      val paraphraseKnowledge1 = Knowledge(lang=lang, sentence=paraphrase2, extentInfoJson = "{}", knowledgeForImages=List(uploadImage(knowledgeForImagePara2aNg, transversalState), uploadImage(knowledgeForImagePara2bNg, transversalState)))
      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1), transversalState)
      
      val propositionIdForInference1 = getUUID()
      val sentenceIdForInference1 = getUUID()
      val premiseKnowledge = List.empty[KnowledgeForParser]
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference1, sentenceIdForInference1, paraphraseKnowledge1))
      val inputSentenceForParser = InputSentenceForParser(premiseKnowledge, claimKnowledge, ActionModeType.DEDUCTION_MODE.index)
      val json = getAnalyzedSentenceObjectsJson(lang, inputSentenceForParser, transversalState)
      //val json = addImageInfoToAnalyzedSentenceObjects(lang=lang, inputSentence, getImageInfo2(List((referencePara2aNg, imageBoxInfoPara2aNg), (referencePara2bNg, imageBoxInfoPara2bNg)), transversalState), transversalState)
      val updatedAsosJson = TestUtils.analyzeByBaseDeductionUnit(json, transversalState)
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(updatedAsosJson))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val verifyingEdgesList: List[VerifyingEdges] = Json.parse(jsonResult).as[List[VerifyingEdges]]
      assert(verifyingEdgesList.map(x => x.coveredPropositionEdges.size).sum == 5)

      TestUtils.checkMatchedBothSide(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=0)   
      TestUtils.checkMatchedOneSide(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=4)   
      TestUtils.checkNoMatch(json = json, sentenceId = sentenceIdForInference1, verifyingEdgesList=verifyingEdgesList, correctSize=1)  
      
    }
  }


}
