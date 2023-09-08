["^ ","~:resource-id",["~:shadow.build.classpath/resource","goog/structs/heap.js"],"~:js","goog.provide(\"goog.structs.Heap\");\ngoog.require(\"goog.array\");\ngoog.require(\"goog.object\");\ngoog.require(\"goog.structs.Node\");\ngoog.structs.Heap = function(opt_heap) {\n  this.nodes_ = [];\n  if (opt_heap) {\n    this.insertAll(opt_heap);\n  }\n};\ngoog.structs.Heap.prototype.insert = function(key, value) {\n  var node = new goog.structs.Node(key, value);\n  var nodes = this.nodes_;\n  nodes.push(node);\n  this.moveUp_(nodes.length - 1);\n};\ngoog.structs.Heap.prototype.insertAll = function(heap) {\n  var keys, values;\n  if (heap instanceof goog.structs.Heap) {\n    keys = heap.getKeys();\n    values = heap.getValues();\n    if (this.getCount() <= 0) {\n      var nodes = this.nodes_;\n      for (var i = 0; i < keys.length; i++) {\n        nodes.push(new goog.structs.Node(keys[i], values[i]));\n      }\n      return;\n    }\n  } else {\n    keys = goog.object.getKeys(heap);\n    values = goog.object.getValues(heap);\n  }\n  for (var i = 0; i < keys.length; i++) {\n    this.insert(keys[i], values[i]);\n  }\n};\ngoog.structs.Heap.prototype.remove = function() {\n  var nodes = this.nodes_;\n  var count = nodes.length;\n  var rootNode = nodes[0];\n  if (count <= 0) {\n    return undefined;\n  } else if (count == 1) {\n    goog.array.clear(nodes);\n  } else {\n    nodes[0] = nodes.pop();\n    this.moveDown_(0);\n  }\n  return rootNode.getValue();\n};\ngoog.structs.Heap.prototype.peek = function() {\n  var nodes = this.nodes_;\n  if (nodes.length == 0) {\n    return undefined;\n  }\n  return nodes[0].getValue();\n};\ngoog.structs.Heap.prototype.peekKey = function() {\n  return this.nodes_[0] && this.nodes_[0].getKey();\n};\ngoog.structs.Heap.prototype.moveDown_ = function(index) {\n  var nodes = this.nodes_;\n  var count = nodes.length;\n  var node = nodes[index];\n  while (index < count >> 1) {\n    var leftChildIndex = this.getLeftChildIndex_(index);\n    var rightChildIndex = this.getRightChildIndex_(index);\n    var smallerChildIndex = rightChildIndex < count && nodes[rightChildIndex].getKey() < nodes[leftChildIndex].getKey() ? rightChildIndex : leftChildIndex;\n    if (nodes[smallerChildIndex].getKey() > node.getKey()) {\n      break;\n    }\n    nodes[index] = nodes[smallerChildIndex];\n    index = smallerChildIndex;\n  }\n  nodes[index] = node;\n};\ngoog.structs.Heap.prototype.moveUp_ = function(index) {\n  var nodes = this.nodes_;\n  var node = nodes[index];\n  while (index > 0) {\n    var parentIndex = this.getParentIndex_(index);\n    if (nodes[parentIndex].getKey() > node.getKey()) {\n      nodes[index] = nodes[parentIndex];\n      index = parentIndex;\n    } else {\n      break;\n    }\n  }\n  nodes[index] = node;\n};\ngoog.structs.Heap.prototype.getLeftChildIndex_ = function(index) {\n  return index * 2 + 1;\n};\ngoog.structs.Heap.prototype.getRightChildIndex_ = function(index) {\n  return index * 2 + 2;\n};\ngoog.structs.Heap.prototype.getParentIndex_ = function(index) {\n  return index - 1 >> 1;\n};\ngoog.structs.Heap.prototype.getValues = function() {\n  var nodes = this.nodes_;\n  var rv = [];\n  var l = nodes.length;\n  for (var i = 0; i < l; i++) {\n    rv.push(nodes[i].getValue());\n  }\n  return rv;\n};\ngoog.structs.Heap.prototype.getKeys = function() {\n  var nodes = this.nodes_;\n  var rv = [];\n  var l = nodes.length;\n  for (var i = 0; i < l; i++) {\n    rv.push(nodes[i].getKey());\n  }\n  return rv;\n};\ngoog.structs.Heap.prototype.containsValue = function(val) {\n  return goog.array.some(this.nodes_, function(node) {\n    return node.getValue() == val;\n  });\n};\ngoog.structs.Heap.prototype.containsKey = function(key) {\n  return goog.array.some(this.nodes_, function(node) {\n    return node.getKey() == key;\n  });\n};\ngoog.structs.Heap.prototype.clone = function() {\n  return new goog.structs.Heap(this);\n};\ngoog.structs.Heap.prototype.getCount = function() {\n  return this.nodes_.length;\n};\ngoog.structs.Heap.prototype.isEmpty = function() {\n  return this.nodes_.length === 0;\n};\ngoog.structs.Heap.prototype.clear = function() {\n  goog.array.clear(this.nodes_);\n};\n","~:source","/**\n * @license\n * Copyright The Closure Library Authors.\n * SPDX-License-Identifier: Apache-2.0\n */\n\n/**\n * @fileoverview Datastructure: Heap.\n *\n *\n * This file provides the implementation of a Heap datastructure. Smaller keys\n * rise to the top.\n *\n * The big-O notation for all operations are below:\n * <pre>\n *  Method          big-O\n * ----------------------------------------------------------------------------\n * - insert         O(logn)\n * - remove         O(logn)\n * - peek           O(1)\n * - contains       O(n)\n * </pre>\n */\n// TODO(user): Should this rely on natural ordering via some Comparable\n//     interface?\n\n\ngoog.provide('goog.structs.Heap');\n\ngoog.require('goog.array');\ngoog.require('goog.object');\ngoog.require('goog.structs.Node');\n\n\n\n/**\n * Class for a Heap datastructure.\n *\n * @param {goog.structs.Heap|Object=} opt_heap Optional goog.structs.Heap or\n *     Object to initialize heap with.\n * @constructor\n * @template K, V\n */\ngoog.structs.Heap = function(opt_heap) {\n  'use strict';\n  /**\n   * The nodes of the heap.\n   * @private\n   * @type {Array<goog.structs.Node>}\n   */\n  this.nodes_ = [];\n\n  if (opt_heap) {\n    this.insertAll(opt_heap);\n  }\n};\n\n\n/**\n * Insert the given value into the heap with the given key.\n * @param {K} key The key.\n * @param {V} value The value.\n */\ngoog.structs.Heap.prototype.insert = function(key, value) {\n  'use strict';\n  var node = new goog.structs.Node(key, value);\n  var nodes = this.nodes_;\n  nodes.push(node);\n  this.moveUp_(nodes.length - 1);\n};\n\n\n/**\n * Adds multiple key-value pairs from another goog.structs.Heap or Object\n * @param {goog.structs.Heap|Object} heap Object containing the data to add.\n */\ngoog.structs.Heap.prototype.insertAll = function(heap) {\n  'use strict';\n  var keys, values;\n  if (heap instanceof goog.structs.Heap) {\n    keys = heap.getKeys();\n    values = heap.getValues();\n\n    // If it is a heap and the current heap is empty, I can rely on the fact\n    // that the keys/values are in the correct order to put in the underlying\n    // structure.\n    if (this.getCount() <= 0) {\n      var nodes = this.nodes_;\n      for (var i = 0; i < keys.length; i++) {\n        nodes.push(new goog.structs.Node(keys[i], values[i]));\n      }\n      return;\n    }\n  } else {\n    keys = goog.object.getKeys(heap);\n    values = goog.object.getValues(heap);\n  }\n\n  for (var i = 0; i < keys.length; i++) {\n    this.insert(keys[i], values[i]);\n  }\n};\n\n\n/**\n * Retrieves and removes the root value of this heap.\n * @return {V} The value removed from the root of the heap.  Returns\n *     undefined if the heap is empty.\n */\ngoog.structs.Heap.prototype.remove = function() {\n  'use strict';\n  var nodes = this.nodes_;\n  var count = nodes.length;\n  var rootNode = nodes[0];\n  if (count <= 0) {\n    return undefined;\n  } else if (count == 1) {\n    goog.array.clear(nodes);\n  } else {\n    nodes[0] = nodes.pop();\n    this.moveDown_(0);\n  }\n  return rootNode.getValue();\n};\n\n\n/**\n * Retrieves but does not remove the root value of this heap.\n * @return {V} The value at the root of the heap. Returns\n *     undefined if the heap is empty.\n */\ngoog.structs.Heap.prototype.peek = function() {\n  'use strict';\n  var nodes = this.nodes_;\n  if (nodes.length == 0) {\n    return undefined;\n  }\n  return nodes[0].getValue();\n};\n\n\n/**\n * Retrieves but does not remove the key of the root node of this heap.\n * @return {K} The key at the root of the heap. Returns undefined if the\n *     heap is empty.\n */\ngoog.structs.Heap.prototype.peekKey = function() {\n  'use strict';\n  return this.nodes_[0] && this.nodes_[0].getKey();\n};\n\n\n/**\n * Moves the node at the given index down to its proper place in the heap.\n * @param {number} index The index of the node to move down.\n * @private\n */\ngoog.structs.Heap.prototype.moveDown_ = function(index) {\n  'use strict';\n  var nodes = this.nodes_;\n  var count = nodes.length;\n\n  // Save the node being moved down.\n  var node = nodes[index];\n  // While the current node has a child.\n  while (index < (count >> 1)) {\n    var leftChildIndex = this.getLeftChildIndex_(index);\n    var rightChildIndex = this.getRightChildIndex_(index);\n\n    // Determine the index of the smaller child.\n    var smallerChildIndex = rightChildIndex < count &&\n            nodes[rightChildIndex].getKey() < nodes[leftChildIndex].getKey() ?\n        rightChildIndex :\n        leftChildIndex;\n\n    // If the node being moved down is smaller than its children, the node\n    // has found the correct index it should be at.\n    if (nodes[smallerChildIndex].getKey() > node.getKey()) {\n      break;\n    }\n\n    // If not, then take the smaller child as the current node.\n    nodes[index] = nodes[smallerChildIndex];\n    index = smallerChildIndex;\n  }\n  nodes[index] = node;\n};\n\n\n/**\n * Moves the node at the given index up to its proper place in the heap.\n * @param {number} index The index of the node to move up.\n * @private\n */\ngoog.structs.Heap.prototype.moveUp_ = function(index) {\n  'use strict';\n  var nodes = this.nodes_;\n  var node = nodes[index];\n\n  // While the node being moved up is not at the root.\n  while (index > 0) {\n    // If the parent is less than the node being moved up, move the parent down.\n    var parentIndex = this.getParentIndex_(index);\n    if (nodes[parentIndex].getKey() > node.getKey()) {\n      nodes[index] = nodes[parentIndex];\n      index = parentIndex;\n    } else {\n      break;\n    }\n  }\n  nodes[index] = node;\n};\n\n\n/**\n * Gets the index of the left child of the node at the given index.\n * @param {number} index The index of the node to get the left child for.\n * @return {number} The index of the left child.\n * @private\n */\ngoog.structs.Heap.prototype.getLeftChildIndex_ = function(index) {\n  'use strict';\n  return index * 2 + 1;\n};\n\n\n/**\n * Gets the index of the right child of the node at the given index.\n * @param {number} index The index of the node to get the right child for.\n * @return {number} The index of the right child.\n * @private\n */\ngoog.structs.Heap.prototype.getRightChildIndex_ = function(index) {\n  'use strict';\n  return index * 2 + 2;\n};\n\n\n/**\n * Gets the index of the parent of the node at the given index.\n * @param {number} index The index of the node to get the parent for.\n * @return {number} The index of the parent.\n * @private\n */\ngoog.structs.Heap.prototype.getParentIndex_ = function(index) {\n  'use strict';\n  return (index - 1) >> 1;\n};\n\n\n/**\n * Gets the values of the heap.\n * @return {!Array<V>} The values in the heap.\n */\ngoog.structs.Heap.prototype.getValues = function() {\n  'use strict';\n  var nodes = this.nodes_;\n  var rv = [];\n  var l = nodes.length;\n  for (var i = 0; i < l; i++) {\n    rv.push(nodes[i].getValue());\n  }\n  return rv;\n};\n\n\n/**\n * Gets the keys of the heap.\n * @return {!Array<K>} The keys in the heap.\n */\ngoog.structs.Heap.prototype.getKeys = function() {\n  'use strict';\n  var nodes = this.nodes_;\n  var rv = [];\n  var l = nodes.length;\n  for (var i = 0; i < l; i++) {\n    rv.push(nodes[i].getKey());\n  }\n  return rv;\n};\n\n\n/**\n * Whether the heap contains the given value.\n * @param {V} val The value to check for.\n * @return {boolean} Whether the heap contains the value.\n */\ngoog.structs.Heap.prototype.containsValue = function(val) {\n  'use strict';\n  return goog.array.some(this.nodes_, function(node) {\n    'use strict';\n    return node.getValue() == val;\n  });\n};\n\n\n/**\n * Whether the heap contains the given key.\n * @param {K} key The key to check for.\n * @return {boolean} Whether the heap contains the key.\n */\ngoog.structs.Heap.prototype.containsKey = function(key) {\n  'use strict';\n  return goog.array.some(this.nodes_, function(node) {\n    'use strict';\n    return node.getKey() == key;\n  });\n};\n\n\n/**\n * Clones a heap and returns a new heap\n * @return {!goog.structs.Heap} A new goog.structs.Heap with the same key-value\n *     pairs.\n */\ngoog.structs.Heap.prototype.clone = function() {\n  'use strict';\n  return new goog.structs.Heap(this);\n};\n\n\n/**\n * The number of key-value pairs in the map\n * @return {number} The number of pairs.\n */\ngoog.structs.Heap.prototype.getCount = function() {\n  'use strict';\n  return this.nodes_.length;\n};\n\n\n/**\n * Returns true if this heap contains no elements.\n * @return {boolean} Whether this heap contains no elements.\n */\ngoog.structs.Heap.prototype.isEmpty = function() {\n  'use strict';\n  return this.nodes_.length === 0;\n};\n\n\n/**\n * Removes all elements from the heap.\n */\ngoog.structs.Heap.prototype.clear = function() {\n  'use strict';\n  goog.array.clear(this.nodes_);\n};\n","~:compiled-at",1693990011651,"~:source-map-json","{\n\"version\":3,\n\"file\":\"goog.structs.heap.js\",\n\"lineCount\":140,\n\"mappings\":\"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\",\n\"sources\":[\"goog/structs/heap.js\"],\n\"sourcesContent\":[\"/**\\n * @license\\n * Copyright The Closure Library Authors.\\n * SPDX-License-Identifier: Apache-2.0\\n */\\n\\n/**\\n * @fileoverview Datastructure: Heap.\\n *\\n *\\n * This file provides the implementation of a Heap datastructure. Smaller keys\\n * rise to the top.\\n *\\n * The big-O notation for all operations are below:\\n * <pre>\\n *  Method          big-O\\n * ----------------------------------------------------------------------------\\n * - insert         O(logn)\\n * - remove         O(logn)\\n * - peek           O(1)\\n * - contains       O(n)\\n * </pre>\\n */\\n// TODO(user): Should this rely on natural ordering via some Comparable\\n//     interface?\\n\\n\\ngoog.provide('goog.structs.Heap');\\n\\ngoog.require('goog.array');\\ngoog.require('goog.object');\\ngoog.require('goog.structs.Node');\\n\\n\\n\\n/**\\n * Class for a Heap datastructure.\\n *\\n * @param {goog.structs.Heap|Object=} opt_heap Optional goog.structs.Heap or\\n *     Object to initialize heap with.\\n * @constructor\\n * @template K, V\\n */\\ngoog.structs.Heap = function(opt_heap) {\\n  'use strict';\\n  /**\\n   * The nodes of the heap.\\n   * @private\\n   * @type {Array<goog.structs.Node>}\\n   */\\n  this.nodes_ = [];\\n\\n  if (opt_heap) {\\n    this.insertAll(opt_heap);\\n  }\\n};\\n\\n\\n/**\\n * Insert the given value into the heap with the given key.\\n * @param {K} key The key.\\n * @param {V} value The value.\\n */\\ngoog.structs.Heap.prototype.insert = function(key, value) {\\n  'use strict';\\n  var node = new goog.structs.Node(key, value);\\n  var nodes = this.nodes_;\\n  nodes.push(node);\\n  this.moveUp_(nodes.length - 1);\\n};\\n\\n\\n/**\\n * Adds multiple key-value pairs from another goog.structs.Heap or Object\\n * @param {goog.structs.Heap|Object} heap Object containing the data to add.\\n */\\ngoog.structs.Heap.prototype.insertAll = function(heap) {\\n  'use strict';\\n  var keys, values;\\n  if (heap instanceof goog.structs.Heap) {\\n    keys = heap.getKeys();\\n    values = heap.getValues();\\n\\n    // If it is a heap and the current heap is empty, I can rely on the fact\\n    // that the keys/values are in the correct order to put in the underlying\\n    // structure.\\n    if (this.getCount() <= 0) {\\n      var nodes = this.nodes_;\\n      for (var i = 0; i < keys.length; i++) {\\n        nodes.push(new goog.structs.Node(keys[i], values[i]));\\n      }\\n      return;\\n    }\\n  } else {\\n    keys = goog.object.getKeys(heap);\\n    values = goog.object.getValues(heap);\\n  }\\n\\n  for (var i = 0; i < keys.length; i++) {\\n    this.insert(keys[i], values[i]);\\n  }\\n};\\n\\n\\n/**\\n * Retrieves and removes the root value of this heap.\\n * @return {V} The value removed from the root of the heap.  Returns\\n *     undefined if the heap is empty.\\n */\\ngoog.structs.Heap.prototype.remove = function() {\\n  'use strict';\\n  var nodes = this.nodes_;\\n  var count = nodes.length;\\n  var rootNode = nodes[0];\\n  if (count <= 0) {\\n    return undefined;\\n  } else if (count == 1) {\\n    goog.array.clear(nodes);\\n  } else {\\n    nodes[0] = nodes.pop();\\n    this.moveDown_(0);\\n  }\\n  return rootNode.getValue();\\n};\\n\\n\\n/**\\n * Retrieves but does not remove the root value of this heap.\\n * @return {V} The value at the root of the heap. Returns\\n *     undefined if the heap is empty.\\n */\\ngoog.structs.Heap.prototype.peek = function() {\\n  'use strict';\\n  var nodes = this.nodes_;\\n  if (nodes.length == 0) {\\n    return undefined;\\n  }\\n  return nodes[0].getValue();\\n};\\n\\n\\n/**\\n * Retrieves but does not remove the key of the root node of this heap.\\n * @return {K} The key at the root of the heap. Returns undefined if the\\n *     heap is empty.\\n */\\ngoog.structs.Heap.prototype.peekKey = function() {\\n  'use strict';\\n  return this.nodes_[0] && this.nodes_[0].getKey();\\n};\\n\\n\\n/**\\n * Moves the node at the given index down to its proper place in the heap.\\n * @param {number} index The index of the node to move down.\\n * @private\\n */\\ngoog.structs.Heap.prototype.moveDown_ = function(index) {\\n  'use strict';\\n  var nodes = this.nodes_;\\n  var count = nodes.length;\\n\\n  // Save the node being moved down.\\n  var node = nodes[index];\\n  // While the current node has a child.\\n  while (index < (count >> 1)) {\\n    var leftChildIndex = this.getLeftChildIndex_(index);\\n    var rightChildIndex = this.getRightChildIndex_(index);\\n\\n    // Determine the index of the smaller child.\\n    var smallerChildIndex = rightChildIndex < count &&\\n            nodes[rightChildIndex].getKey() < nodes[leftChildIndex].getKey() ?\\n        rightChildIndex :\\n        leftChildIndex;\\n\\n    // If the node being moved down is smaller than its children, the node\\n    // has found the correct index it should be at.\\n    if (nodes[smallerChildIndex].getKey() > node.getKey()) {\\n      break;\\n    }\\n\\n    // If not, then take the smaller child as the current node.\\n    nodes[index] = nodes[smallerChildIndex];\\n    index = smallerChildIndex;\\n  }\\n  nodes[index] = node;\\n};\\n\\n\\n/**\\n * Moves the node at the given index up to its proper place in the heap.\\n * @param {number} index The index of the node to move up.\\n * @private\\n */\\ngoog.structs.Heap.prototype.moveUp_ = function(index) {\\n  'use strict';\\n  var nodes = this.nodes_;\\n  var node = nodes[index];\\n\\n  // While the node being moved up is not at the root.\\n  while (index > 0) {\\n    // If the parent is less than the node being moved up, move the parent down.\\n    var parentIndex = this.getParentIndex_(index);\\n    if (nodes[parentIndex].getKey() > node.getKey()) {\\n      nodes[index] = nodes[parentIndex];\\n      index = parentIndex;\\n    } else {\\n      break;\\n    }\\n  }\\n  nodes[index] = node;\\n};\\n\\n\\n/**\\n * Gets the index of the left child of the node at the given index.\\n * @param {number} index The index of the node to get the left child for.\\n * @return {number} The index of the left child.\\n * @private\\n */\\ngoog.structs.Heap.prototype.getLeftChildIndex_ = function(index) {\\n  'use strict';\\n  return index * 2 + 1;\\n};\\n\\n\\n/**\\n * Gets the index of the right child of the node at the given index.\\n * @param {number} index The index of the node to get the right child for.\\n * @return {number} The index of the right child.\\n * @private\\n */\\ngoog.structs.Heap.prototype.getRightChildIndex_ = function(index) {\\n  'use strict';\\n  return index * 2 + 2;\\n};\\n\\n\\n/**\\n * Gets the index of the parent of the node at the given index.\\n * @param {number} index The index of the node to get the parent for.\\n * @return {number} The index of the parent.\\n * @private\\n */\\ngoog.structs.Heap.prototype.getParentIndex_ = function(index) {\\n  'use strict';\\n  return (index - 1) >> 1;\\n};\\n\\n\\n/**\\n * Gets the values of the heap.\\n * @return {!Array<V>} The values in the heap.\\n */\\ngoog.structs.Heap.prototype.getValues = function() {\\n  'use strict';\\n  var nodes = this.nodes_;\\n  var rv = [];\\n  var l = nodes.length;\\n  for (var i = 0; i < l; i++) {\\n    rv.push(nodes[i].getValue());\\n  }\\n  return rv;\\n};\\n\\n\\n/**\\n * Gets the keys of the heap.\\n * @return {!Array<K>} The keys in the heap.\\n */\\ngoog.structs.Heap.prototype.getKeys = function() {\\n  'use strict';\\n  var nodes = this.nodes_;\\n  var rv = [];\\n  var l = nodes.length;\\n  for (var i = 0; i < l; i++) {\\n    rv.push(nodes[i].getKey());\\n  }\\n  return rv;\\n};\\n\\n\\n/**\\n * Whether the heap contains the given value.\\n * @param {V} val The value to check for.\\n * @return {boolean} Whether the heap contains the value.\\n */\\ngoog.structs.Heap.prototype.containsValue = function(val) {\\n  'use strict';\\n  return goog.array.some(this.nodes_, function(node) {\\n    'use strict';\\n    return node.getValue() == val;\\n  });\\n};\\n\\n\\n/**\\n * Whether the heap contains the given key.\\n * @param {K} key The key to check for.\\n * @return {boolean} Whether the heap contains the key.\\n */\\ngoog.structs.Heap.prototype.containsKey = function(key) {\\n  'use strict';\\n  return goog.array.some(this.nodes_, function(node) {\\n    'use strict';\\n    return node.getKey() == key;\\n  });\\n};\\n\\n\\n/**\\n * Clones a heap and returns a new heap\\n * @return {!goog.structs.Heap} A new goog.structs.Heap with the same key-value\\n *     pairs.\\n */\\ngoog.structs.Heap.prototype.clone = function() {\\n  'use strict';\\n  return new goog.structs.Heap(this);\\n};\\n\\n\\n/**\\n * The number of key-value pairs in the map\\n * @return {number} The number of pairs.\\n */\\ngoog.structs.Heap.prototype.getCount = function() {\\n  'use strict';\\n  return this.nodes_.length;\\n};\\n\\n\\n/**\\n * Returns true if this heap contains no elements.\\n * @return {boolean} Whether this heap contains no elements.\\n */\\ngoog.structs.Heap.prototype.isEmpty = function() {\\n  'use strict';\\n  return this.nodes_.length === 0;\\n};\\n\\n\\n/**\\n * Removes all elements from the heap.\\n */\\ngoog.structs.Heap.prototype.clear = function() {\\n  'use strict';\\n  goog.array.clear(this.nodes_);\\n};\\n\"],\n\"names\":[\"goog\",\"provide\",\"require\",\"structs\",\"Heap\",\"goog.structs.Heap\",\"opt_heap\",\"nodes_\",\"insertAll\",\"prototype\",\"insert\",\"goog.structs.Heap.prototype.insert\",\"key\",\"value\",\"node\",\"Node\",\"nodes\",\"push\",\"moveUp_\",\"length\",\"goog.structs.Heap.prototype.insertAll\",\"heap\",\"keys\",\"values\",\"getKeys\",\"getValues\",\"getCount\",\"i\",\"object\",\"remove\",\"goog.structs.Heap.prototype.remove\",\"count\",\"rootNode\",\"undefined\",\"array\",\"clear\",\"pop\",\"moveDown_\",\"getValue\",\"peek\",\"goog.structs.Heap.prototype.peek\",\"peekKey\",\"goog.structs.Heap.prototype.peekKey\",\"getKey\",\"goog.structs.Heap.prototype.moveDown_\",\"index\",\"leftChildIndex\",\"getLeftChildIndex_\",\"rightChildIndex\",\"getRightChildIndex_\",\"smallerChildIndex\",\"goog.structs.Heap.prototype.moveUp_\",\"parentIndex\",\"getParentIndex_\",\"goog.structs.Heap.prototype.getLeftChildIndex_\",\"goog.structs.Heap.prototype.getRightChildIndex_\",\"goog.structs.Heap.prototype.getParentIndex_\",\"goog.structs.Heap.prototype.getValues\",\"rv\",\"l\",\"goog.structs.Heap.prototype.getKeys\",\"containsValue\",\"goog.structs.Heap.prototype.containsValue\",\"val\",\"some\",\"containsKey\",\"goog.structs.Heap.prototype.containsKey\",\"clone\",\"goog.structs.Heap.prototype.clone\",\"goog.structs.Heap.prototype.getCount\",\"isEmpty\",\"goog.structs.Heap.prototype.isEmpty\",\"goog.structs.Heap.prototype.clear\"]\n}\n"]