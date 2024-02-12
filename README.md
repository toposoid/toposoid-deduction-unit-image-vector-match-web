# toposoid-deduction-unit-image-vector-match-web
This is a WEB API that works as a microservice within the toposoid project.
Toposoid is a knowledge base construction platform.(see [Toposoid　Root Project](https://github.com/toposoid/toposoid.git))
This microservice makes inferences by matching images to a knowledge database.


* API Image
    * Input
    * <img width="1018" src="https://github.com/toposoid/toposoid-deduction-unit-image-vector-match-web/assets/82787843/9a84a768-b33b-4bcd-be79-352c7be28e58">
    * Output
    * <img width="1026" src="https://github.com/toposoid/toposoid-deduction-unit-image-vector-match-web/assets/82787843/cc2b3243-bba9-491e-a1cb-2e266c773141">

## Requirements
* Docker version 20.10.x, or late
* docker-compose version 1.22.x
* The following microservices must be running
    * scala-data-accessor-neo4j-web
    * toposoid-common-nlp-japanese-web
    * toposoid-common-nlp-english-web
    * toposoid-common-image-recognition-web
    * data-accessor-weaviate-web
    * semitechnologies/weaviate
    * neo4j

## Recommended Environment For Standalone
* Required: at least 16GB of RAM
* Required: at least 40G of HDD(Total required Docker Image size)
* Please understand that since we are dealing with large models such as LLM, the Dockerfile size is large and the required machine SPEC is high.


## Setup For Standalone
```bssh
docker-compose up
```
The first startup takes a long time until docker pull finishes.
## Usage
```bash
# Please refer to the following for information on registering data to try deduction.
# ref. https://github.com/toposoid/toposoid-knowledge-register-web
#for example
curl -X POST -H "Content-Type: application/json" -d '{
    "premiseList": [],
    "premiseLogicRelation": [],
    "claimList": [
        {
            "sentence": "猫が２匹います。",
            "lang": "ja_JP",
            "extentInfoJson": "{}",
            "isNegativeSentence": false,
            "knowledgeForImages":[{
                "id": "",
                "imageReference": {
                    "reference": {
                        "url": "",
                        "surface": "猫が",
                        "surfaceIndex": 0,
                        "isWholeSentence": false,
                        "originalUrlOrReference": "http://images.cocodataset.org/val2017/000000039769.jpg"},
                    "x": 11,
                    "y": 11,
                    "width": 446,
                    "height": 310
                }
            }]
        }
    ],
    "claimLogicRelation": [
    ]
}' http://localhost:9002/regist

# Deduction
curl -X POST -H "Content-Type: application/json" -d '{
    "analyzedSentenceObjects": [
        {
            "nodeMap": {
                "90a5ac72-1a35-4450-9f9b-e0458cc24e19-2": {
                    "nodeId": "90a5ac72-1a35-4450-9f9b-e0458cc24e19-2",
                    "propositionId": "e8710976-0779-436c-8070-2c57cb802b1f",
                    "sentenceId": "90a5ac72-1a35-4450-9f9b-e0458cc24e19",
                    "predicateArgumentStructure": {
                        "currentId": 2,
                        "parentId": -1,
                        "isMainSection": true,
                        "surface": "います。",
                        "normalizedName": "射る",
                        "dependType": "D",
                        "caseType": "文末",
                        "isDenialWord": false,
                        "isConditionalConnection": false,
                        "normalizedNameYomi": "いる?居る",
                        "surfaceYomi": "います。",
                        "modalityType": "-",
                        "parallelType": "-",
                        "nodeType": 1,
                        "morphemes": [
                            "動詞,*,母音動詞,基本連用形",
                            "接尾辞,動詞性接尾辞,動詞性接尾辞ます型,基本形",
                            "特殊,句点,*,*"
                        ]
                    },
                    "localContext": {
                        "lang": "ja_JP",
                        "namedEntity": "",
                        "rangeExpressions": {
                            "": {}
                        },
                        "categories": {
                            "": ""
                        },
                        "domains": {
                            "": ""
                        },
                        "knowledgeFeatureReferences": []
                    }
                },
                "90a5ac72-1a35-4450-9f9b-e0458cc24e19-1": {
                    "nodeId": "90a5ac72-1a35-4450-9f9b-e0458cc24e19-1",
                    "propositionId": "e8710976-0779-436c-8070-2c57cb802b1f",
                    "sentenceId": "90a5ac72-1a35-4450-9f9b-e0458cc24e19",
                    "predicateArgumentStructure": {
                        "currentId": 1,
                        "parentId": 2,
                        "isMainSection": false,
                        "surface": "２匹",
                        "normalizedName": "２",
                        "dependType": "D",
                        "caseType": "無格",
                        "isDenialWord": false,
                        "isConditionalConnection": false,
                        "normalizedNameYomi": "にひき",
                        "surfaceYomi": "にひき",
                        "modalityType": "-",
                        "parallelType": "-",
                        "nodeType": 1,
                        "morphemes": [
                            "名詞,数詞,*,*",
                            "接尾辞,名詞性名詞助数辞,*,*"
                        ]
                    },
                    "localContext": {
                        "lang": "ja_JP",
                        "namedEntity": "",
                        "rangeExpressions": {
                            "２": {
                                "prefix": "",
                                "quantity": "2",
                                "unit": "匹",
                                "range": "2"
                            }
                        },
                        "categories": {
                            "２": "数量"
                        },
                        "domains": {
                            "": ""
                        },
                        "knowledgeFeatureReferences": []
                    }
                },
                "90a5ac72-1a35-4450-9f9b-e0458cc24e19-0": {
                    "nodeId": "90a5ac72-1a35-4450-9f9b-e0458cc24e19-0",
                    "propositionId": "e8710976-0779-436c-8070-2c57cb802b1f",
                    "sentenceId": "90a5ac72-1a35-4450-9f9b-e0458cc24e19",
                    "predicateArgumentStructure": {
                        "currentId": 0,
                        "parentId": 2,
                        "isMainSection": false,
                        "surface": "ペットが",
                        "normalizedName": "ペット",
                        "dependType": "D",
                        "caseType": "ガ格",
                        "isDenialWord": false,
                        "isConditionalConnection": false,
                        "normalizedNameYomi": "ぺっと",
                        "surfaceYomi": "ぺっとが",
                        "modalityType": "-",
                        "parallelType": "-",
                        "nodeType": 1,
                        "morphemes": [
                            "名詞,普通名詞,*,*",
                            "助詞,格助詞,*,*"
                        ]
                    },
                    "localContext": {
                        "lang": "ja_JP",
                        "namedEntity": "",
                        "rangeExpressions": {
                            "": {}
                        },
                        "categories": {
                            "ペット": "動物"
                        },
                        "domains": {
                            "ペット": "家庭・暮らし"
                        },
                        "knowledgeFeatureReferences": [
                            {
                                "propositionId": "e8710976-0779-436c-8070-2c57cb802b1f",
                                "sentenceId": "90a5ac72-1a35-4450-9f9b-e0458cc24e19",
                                "featureId": "38bbb1e5-cd40-41a5-99d9-6221299ecec2",
                                "featureType": 1,
                                "url": "http://toposoid-contents-admin-web:9012/contents/images/ace2bbe5-08e0-4ab6-8d20-d79423ee3719.jpeg",
                                "source": "http://images.cocodataset.org/val2017/000000039769.jpg",
                                "featureInputType": 0,
                                "extentText": "{}"
                            }
                        ]
                    }
                }
            },
            "edgeList": [
                {
                    "sourceId": "90a5ac72-1a35-4450-9f9b-e0458cc24e19-1",
                    "destinationId": "90a5ac72-1a35-4450-9f9b-e0458cc24e19-2",
                    "caseStr": "無格",
                    "dependType": "D",
                    "parallelType": "-",
                    "hasInclusion": false,
                    "logicType": "-"
                },
                {
                    "sourceId": "90a5ac72-1a35-4450-9f9b-e0458cc24e19-0",
                    "destinationId": "90a5ac72-1a35-4450-9f9b-e0458cc24e19-2",
                    "caseStr": "ガ格",
                    "dependType": "D",
                    "parallelType": "-",
                    "hasInclusion": false,
                    "logicType": "-"
                }
            ],
            "knowledgeBaseSemiGlobalNode": {
                "nodeId": "90a5ac72-1a35-4450-9f9b-e0458cc24e19",
                "propositionId": "e8710976-0779-436c-8070-2c57cb802b1f",
                "sentenceId": "90a5ac72-1a35-4450-9f9b-e0458cc24e19",
                "sentence": "ペットが２匹います。",
                "sentenceType": 1,
                "localContextForFeature": {
                    "lang": "ja_JP",
                    "knowledgeFeatureReferences": []
                }
            },
            "deductionResult": {
                "status": false,
                "coveredPropositionResults": [],
                "havePremiseInGivenProposition": false
            }
        }
    ]
}' http://localhost:9104/execute
```

## For details on Input Json
see below.
* ref. https://github.com/toposoid/toposoid-deduction-admin-web?tab=readme-ov-file#json-details

# Note
* This microservice uses 9104 as the default port.
* If you want to run in a remote environment or a virtual environment, change PRIVATE_IP_ADDRESS in docker-compose.yml according to your environment.

## License
toposoid/toposoid-deduction-unit-image-vector-match-web is Open Source software released under the [Apache 2.0 license](https://www.apache.org/licenses/LICENSE-2.0.html).

## Author
* Makoto Kubodera([Linked Ideal LLC.](https://linked-ideal.com/))

Thank you!
