# AVLTreeMap

## What is AVL?

you can find the definition of AVLTree in [Wiki](https://en.wikipedia.org/wiki/AVL_tree)

## Basic Use

`AVLTreeMap` implements interface `Map<K,V>`

so you can use it as a single map

and of course, it is `thread safe`

> Insert or Update

```java
map.put(someKey, someValue);
```

> Atomic value combination

```
map.put(someKey, (old) -> {
    if (old == null) {
        return someValue
    } else {
        return doSomeThing(someValue, old)
    }
})
```


> Page Query with offset and limit (Just like `java.util.TreeMap`  which is implements by Red Black Tree)

```
//query top n
LinkedHashMap<K,V> result = map.max(offset, limit);
//query last n
LinkedHashMap<K,V> result = map.min(offset, limit)
```
