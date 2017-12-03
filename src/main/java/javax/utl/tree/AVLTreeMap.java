package javax.utl.tree;

import javafx.util.Pair;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.Supplier;

/***
 * AVLTree, a kind of balance tree
 *
 * - Thread Safe
 * - Basic K-V map store
 * - Top N search, with query limit and offset
 *
 * @see <a href="https://en.wikipedia.org/wiki/AVL_tree">https://en.wikipedia.org/wiki/AVL_tree</a>
 * @param <K> key
 * @param <V> value
 * @author chpengzh@foxmail.com
 */
public class AVLTreeMap<K extends Comparable<K>, V> implements Map<K, V> {

    private static final boolean DEBUG = false;

    private volatile Node<K, V> root;
    private volatile int size;
    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();

    /***
     * atomic value update with combiner function
     *
     * @param key update key
     * @param combiner combiner function, which may passed by origin value in map store
     * @return whether the map is update
     * @see #put(Comparable, Object)
     */
    public boolean put(K key, Function<V, V> combiner) {
        return _lock(false, () -> {
            ThreadLocal<Boolean> flag = ThreadLocal.withInitial(() -> false);
            root = Impl.insert(root, key, (o) -> {
                flag.set(true);
                return combiner.apply(o);
            });
            boolean insert = flag.get();
            if (insert) size++;
            return insert;
        });
    }

    /***
     * the height of tree
     *
     * @return tree height
     */
    public int height() {
        return Impl.height(root);
    }

    /***
     * max page by key
     *
     * @param offset start offset
     * @param limit fetch limit
     * @return the K-V result within the tree map store
     */
    public LinkedHashMap<K, V> max(int offset, int limit) {
        return _lock(true, () -> {
            LinkedHashMap<K, V> result = new LinkedHashMap<>();
            Impl.maxN(root, offset, limit, result, ThreadLocal.withInitial(() -> 0));
            return result;
        });
    }

    /***
     * min page by key
     *
     * @param offset start offset
     * @param limit fetch limit
     * @return the K-V result within the tree map store
     */
    public LinkedHashMap<K, V> min(int offset, int limit) {
        return _lock(true, () -> {
            LinkedHashMap<K, V> result = new LinkedHashMap<>();
            Impl.minN(root, offset, limit, result, ThreadLocal.withInitial(() -> 0));
            return result;
        });
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return root == null;
    }

    @Override
    public boolean containsKey(Object key) {
        @SuppressWarnings("unchecked") K _key = (K) key;
        return _lock(true, () -> Impl.containsKey(root, _key));
    }

    @Override
    public boolean containsValue(Object value) {
        @SuppressWarnings("unchecked") V _value = (V) value;
        return _lock(true, () -> Impl.containsValue(root, _value));
    }

    @Override
    public V get(Object key) {
        @SuppressWarnings("unchecked") K _key = (K) key;
        return _lock(true, () -> {
            Node<K, V> node = Impl.get(root, _key);
            return node == null ? null : node.value;
        });
    }

    @Override
    public V put(K key, V value) {
        put(key, (o) -> value);
        return value;
    }

    @Override
    public V remove(Object key) {
        @SuppressWarnings("unchecked") K _key = (K) key;
        return _lock(false, () -> {
            ThreadLocal<Pair<Boolean, V>> valueRef = ThreadLocal.withInitial(() -> null);
            root = Impl.delete(root, _key, valueRef);
            Pair<Boolean, V> result = valueRef.get();
            if (result != null) size--;
            return result == null ? null : result.getValue();
        });
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        _lock(false, () -> {
            m.forEach((k, v) -> put(k, (o) -> v));
            return null;
        });
    }

    @Override
    public void clear() {
        _lock(false, () -> {
            root = null;
            size = 0;
            return null;
        });
    }

    @Override
    public Set<K> keySet() {
        return _lock(true, () -> {
            Set<K> result = new HashSet<>();
            Impl.keySet(root, result);
            return result;
        });
    }

    @Override
    public Collection<V> values() {
        return _lock(true, () -> {
            List<V> result = new ArrayList<>();
            Impl.valueCollection(root, result);
            return result;
        });
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return _lock(true, () -> {
            Set<Entry<K, V>> result = new HashSet<>();
            Impl.entrySet(root, result);
            return result;
        });
    }

    @Override
    public String toString() {
        return _lock(true, () -> {
//            try {
//                return mapper.writeValueAsString(root);
//            } catch (JsonProcessingException e) {
//                return null;
//            }
            StringBuilder sb = new StringBuilder();
            Impl.print(root, sb);
            if (root != null) sb.setLength(sb.length() - ",".length());
            return sb.toString();
        });
    }

    public boolean isBalance() {
        return _lock(true, () -> Impl.isBalanced(root, ThreadLocal.withInitial(() -> 0)));
    }

    private <O> O _lock(final boolean readOnly, final Supplier<O> task) {
        Lock l = readOnly ? readWriteLock.readLock() : readWriteLock.writeLock();
        l.lock();
        try {
            O result = task.get();
            if (!readOnly && DEBUG) System.out.println(this);
            return result;
        } finally {
            l.unlock();
        }
    }

    //@JsonInclude(value = JsonInclude.Include.NON_NULL)
    private static class Node<K extends Comparable<K>, V> {
        private volatile K key;
        private volatile V value;
        private volatile int height = 1;
        private Node<K, V> left, right;

        Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    private static class Impl {

        private static <K extends Comparable<K>, V>
        void keySet(
                final Node<K, V> N,
                final Set<K> result
        ) {
            if (N == null) return;
            if (N.left != null) keySet(N.left, result);
            result.add(N.key);
            if (N.right != null) keySet(N.right, result);
        }

        private static <K extends Comparable<K>, V>
        void valueCollection(
                final Node<K, V> N,
                final Collection<V> result
        ) {
            if (N == null) return;
            if (N.left != null) valueCollection(N.left, result);
            result.add(N.value);
            if (N.right != null) valueCollection(N.right, result);
        }

        private static <K extends Comparable<K>, V>
        void entrySet(
                final Node<K, V> N,
                final Set<Entry<K, V>> result
        ) {
            if (N == null) return;
            if (N.left != null) entrySet(N.left, result);
            result.add(new Entry<K, V>() {
                @Override
                public K getKey() {
                    return N.key;
                }

                @Override
                public V getValue() {
                    return N.value;
                }

                @Override
                public V setValue(V value) {
                    return N.value = value;
                }
            });
            if (N.right != null) entrySet(N.right, result);
        }

        private static <K extends Comparable<K>, V>
        void maxN(
                final Node<K, V> N,
                final int offset,
                final int limit,
                final LinkedHashMap<K, V> result,
                final ThreadLocal<Integer> counter
        ) {
            if (N == null) return;
            if (N.right != null) maxN(N.right, offset, limit, result, counter);
            int id = counter.get() + 1;
            counter.set(id);
            if (id > offset + limit) return;
            if (offset < id) result.put(N.key, N.value);
            if (N.left != null) maxN(N.left, offset, limit, result, counter);
        }

        private static <K extends Comparable<K>, V>
        void minN(
                final Node<K, V> N,
                final int offset,
                final int limit,
                final LinkedHashMap<K, V> result,
                final ThreadLocal<Integer> counter
        ) {
            if (N == null) return;
            if (N.left != null) minN(N.left, offset, limit, result, counter);
            int id = counter.get() + 1;
            counter.set(id);
            if (id > offset + limit) return;
            if (offset < id) result.put(N.key, N.value);
            if (N.right != null) minN(N.right, offset, limit, result, counter);
        }

        private static <K extends Comparable<K>, V>
        int height(
                final Node<K, V> N
        ) {
            return N == null ? 0 : N.height;
        }

        private static <K extends Comparable<K>, V>
        int getBalance(
                final Node<K, V> N
        ) {
            return (N == null) ? 0 : (height(N.left) - height(N.right));
        }

        private static <K extends Comparable<K>, V>
        Node<K, V> insert(
                final Node<K, V> N,
                final K key,
                final Function<V, V> combiner
        ) {
            /* 1.  Perform the normal BST rotation */
            if (N == null) {
                return new Node<>(key, combiner.apply(null));
            }

            int compare = key.compareTo(N.key);
            if (compare < 0) {
                N.left = insert(N.left, key, combiner);
            } else if (compare > 0) {
                N.right = insert(N.right, key, combiner);
            } else {
                N.value = combiner.apply(N.value);
                return N;
            }

            /* 2. Update height of this ancestor node */
            N.height = 1 + Math.max(height(N.left),
                    height(N.right));

            /* 3. Get the balance factor of this ancestor
               node to check whether this node became
               Wunbalanced */
            int balance = getBalance(N);

            // If this node becomes unbalanced, then
            // there are 4 cases Left Left Case
            if (balance > 1 && key.compareTo(N.left.key) < 0)
                return rightRotate(N);

            // Right Right Case
            if (balance < -1 && key.compareTo(N.right.key) > 0)
                return leftRotate(N);

            // Left Right Case
            if (balance > 1 && key.compareTo(N.left.key) > 0) {
                N.left = leftRotate(N.left);
                return rightRotate(N);
            }

            // Right Left Case
            if (balance < -1 && key.compareTo(N.right.key) < 0) {
                N.right = rightRotate(N.right);
                return leftRotate(N);
            }

        /* return the (unchanged) node pointer */
            return N;
        }

        private static <K extends Comparable<K>, V>
        Node<K, V> get(
                final Node<K, V> N,
                final K key
        ) {
            if (N == null) return null;

            int compare = key.compareTo(N.key);
            if (compare < 0) {
                return get(N.left, key);
            } else if (compare > 0) {
                return get(N.right, key);
            } else {
                return N;
            }
        }

        private static <K extends Comparable<K>, V>
        boolean containsKey(
                final Node<K, V> N,
                final K key
        ) {
            if (N == null) return false;
            int compare = key.compareTo(N.key);
            return compare == 0 || containsKey(compare < 0 ? N.left : N.right, key);
        }

        private static <K extends Comparable<K>, V>
        boolean containsValue(
                final Node<K, V> N,
                final V value
        ) {
            return N != null && (Objects.equals(N.value, value)
                    || N.left != null && containsValue(N.left, value)
                    || N.right != null && containsValue(N.right, value));
        }

        private static <K extends Comparable<K>, V>
        Node<K, V> minValueNode(
                final Node<K, V> N
        ) {
            Node<K, V> current = N;

        /* loop down to find the leftmost leaf */
            while (current.left != null)
                current = current.left;

            return current;
        }

        private static <K extends Comparable<K>, V>
        Node<K, V> delete(
                Node<K, V> root,
                final K key,
                final ThreadLocal<Pair<Boolean, V>> value
        ) {
            // STEP 1: PERFORM STANDARD BST DELETE
            if (root == null) return null;

            // If the key to be deleted is smaller than
            // the root's key, then it lies in left subtree
            int compare = key.compareTo(root.key);
            if (compare < 0)
                root.left = delete(root.left, key, value);

                // If the key to be deleted is greater than the
                // root's key, then it lies in right subtree
            else if (compare > 0)
                root.right = delete(root.right, key, value);

                // if key is same as root's key, then this is the node
                // to be deleted
            else {
                if (value != null && value.get() == null)
                    value.set(new Pair<>(true, root.value));

                // node with only one child or no child
                if ((root.left == null) || (root.right == null)) {
                    Node<K, V> temp = null;
                    if (null == root.left)
                        temp = root.right;
                    else
                        temp = root.left;

                    // No child case
                    if (temp == null) {
                        root = null;
                    } else   // One child case
                        root = temp; // Copy the contents of
                    // the non-empty child
                } else {

                    // node with two children: Get the inorder
                    // successor (smallest in the right subtree)
                    Node<K, V> temp = minValueNode(root.right);

                    // Copy the inorder successor's data to this node
                    root.key = temp.key;
                    root.value = temp.value;

                    // Delete the inorder successor
                    root.right = delete(root.right, temp.key, value);
                }
            }

            // If the tree had only one node then return
            if (root == null) return null;

            // STEP 2: UPDATE HEIGHT OF THE CURRENT NODE
            root.height = Math.max(height(root.left), height(root.right)) + 1;

            // STEP 3: GET THE BALANCE FACTOR OF THIS NODE (to check whether
            //  this node became unbalanced)
            int balance = getBalance(root);

            // If this node becomes unbalanced, then there are 4 cases
            // Left Left Case
            if (balance > 1 && getBalance(root.left) >= 0)
                return rightRotate(root);

            // Left Right Case
            if (balance > 1 && getBalance(root.left) < 0) {
                root.left = leftRotate(root.left);
                return rightRotate(root);
            }

            // Right Right Case
            if (balance < -1 && getBalance(root.right) <= 0)
                return leftRotate(root);

            // Right Left Case
            if (balance < -1 && getBalance(root.right) > 0) {
                root.right = rightRotate(root.right);
                return leftRotate(root);
            }

            return root;
        }

        private static <K extends Comparable<K>, V>
        Node<K, V> rightRotate(
                final Node<K, V> y
        ) {
            Node<K, V> x = y.left;
            Node<K, V> T2 = x.right;

            // Perform rotation
            x.right = y;
            y.left = T2;

            // Update heights
            y.height = Math.max(height(y.left), height(y.right)) + 1;
            x.height = Math.max(height(x.left), height(x.right)) + 1;

            // Return new root
            return x;
        }

        private static <K extends Comparable<K>, V>
        Node<K, V> leftRotate(
                final Node<K, V> x
        ) {
            Node<K, V> y = x.right;
            Node<K, V> T2 = y.left;

            // Perform rotation
            y.left = x;
            x.right = T2;

            //  Update heights
            x.height = Math.max(height(x.left), height(x.right)) + 1;
            y.height = Math.max(height(y.left), height(y.right)) + 1;

            // Return new root
            return y;
        }

        private static <K extends Comparable<K>, V>
        boolean isBalanced(
                final Node<K, V> N,
                final ThreadLocal<Integer> height
        ) {
            if (N == null) return true;
            if (N.left == null && N.right == null) return true;
            ThreadLocal<Integer> lh = ThreadLocal.withInitial(() -> 0), rh = ThreadLocal.withInitial(() -> 0);
            if (N.left != null && !isBalanced(N.left, lh)) return false;
            if (N.right != null && !isBalanced(N.right, rh)) return false;
            if (Math.abs(lh.get() - rh.get()) > 1) return false;
            height.set(Math.max(lh.get(), rh.get()) + 1);
            return true;
        }

        private static <K extends Comparable<K>, V>
        void print(
                final Node<K, V> N,
                StringBuilder sb
        ) {
            if (N == null) {
                sb.append("null");
                return;
            }
            if (N.left != null) print(N.left, sb);
            sb.append(N.key).append(",");
            if (N.right != null) print(N.right, sb);
        }
    }

}