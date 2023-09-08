["^ ","~:resource-id",["~:shadow.build.classpath/resource","goog/async/throwexception.js"],"~:js","goog.loadModule(function(exports) {\n  \"use strict\";\n  goog.module(\"goog.async.throwException\");\n  goog.module.declareLegacyNamespace();\n  function throwException(exception) {\n    goog.global.setTimeout(() => {\n      throw exception;\n    }, 0);\n  }\n  exports = throwException;\n  return exports;\n});\n","~:source","/**\n * @license\n * Copyright The Closure Library Authors.\n * SPDX-License-Identifier: Apache-2.0\n */\n\n/**\n * @fileoverview Provides a function to throw an error without interrupting\n * the current execution context.\n */\n\ngoog.module('goog.async.throwException');\ngoog.module.declareLegacyNamespace();\n\n/**\n * Throw an item without interrupting the current execution context.  For\n * example, if processing a group of items in a loop, sometimes it is useful\n * to report an error while still allowing the rest of the batch to be\n * processed.\n * @param {*} exception\n */\nfunction throwException(exception) {\n  // Each throw needs to be in its own context.\n  goog.global.setTimeout(() => {\n    throw exception;\n  }, 0);\n}\nexports = throwException;\n","~:compiled-at",1693990011528,"~:source-map-json","{\n\"version\":3,\n\"file\":\"goog.async.throwexception.js\",\n\"lineCount\":13,\n\"mappings\":\"AAAA,IAAA,CAAA,UAAA,CAAA,QAAA,CAAA,OAAA,CAAA;AAAA,cAAA;AAWAA,MAAKC,CAAAA,MAAL,CAAY,2BAAZ,CAAA;AACAD,MAAKC,CAAAA,MAAOC,CAAAA,sBAAZ,EAAA;AASAC,UAASA,eAAc,CAACC,SAAD,CAAY;AAEjCJ,QAAKK,CAAAA,MAAOC,CAAAA,UAAZ,CAAuB,EAAA,IAAM;AAC3B,YAAMF,SAAN;AAD2B,KAA7B,EAEG,CAFH,CAAA;AAFiC;AAMnCG,SAAA,GAAUJ,cAAV;AA3BA,SAAA,OAAA;AAAA,CAAA,CAAA;;\",\n\"sources\":[\"goog/async/throwexception.js\"],\n\"sourcesContent\":[\"/**\\n * @license\\n * Copyright The Closure Library Authors.\\n * SPDX-License-Identifier: Apache-2.0\\n */\\n\\n/**\\n * @fileoverview Provides a function to throw an error without interrupting\\n * the current execution context.\\n */\\n\\ngoog.module('goog.async.throwException');\\ngoog.module.declareLegacyNamespace();\\n\\n/**\\n * Throw an item without interrupting the current execution context.  For\\n * example, if processing a group of items in a loop, sometimes it is useful\\n * to report an error while still allowing the rest of the batch to be\\n * processed.\\n * @param {*} exception\\n */\\nfunction throwException(exception) {\\n  // Each throw needs to be in its own context.\\n  goog.global.setTimeout(() => {\\n    throw exception;\\n  }, 0);\\n}\\nexports = throwException;\\n\"],\n\"names\":[\"goog\",\"module\",\"declareLegacyNamespace\",\"throwException\",\"exception\",\"global\",\"setTimeout\",\"exports\"]\n}\n"]