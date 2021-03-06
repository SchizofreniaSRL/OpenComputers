package li.cil.oc.api.network;

/**
 * This interface provides access to arguments passed to a {@link LuaCallback}.
 * <p/>
 * It allows checking for the presence of arguments in a uniform manner, taking
 * care of proper type checking based on what can be passed along by Lua.
 * <p/>
 * Note that integer values fetched this way are actually double values that
 * have been truncated. So if a Lua program passes <tt>1.9</tt> and you do a
 * <tt>checkInteger</tt> you'll get a <tt>1</tt>.
 */
public interface Arguments extends Iterable<Object> {
    /**
     * The total number of arguments that were passed to the function.
     */
    int count();

    /**
     * Get whatever is at the specified index.
     * <p/>
     * Throws an error if there are too few arguments.
     * <p/>
     * The returned object will be one of the following, based on the conversion
     * performed internally:
     * <ul>
     * <li><tt>null</tt> if the Lua value was <tt>nil</tt>.</li>
     * <li><tt>java.lang.Boolean</tt> if the Lua value was a boolean.</li>
     * <li><tt>java.lang.Double</tt> if the Lua value was a number.</li>
     * <li><tt>byte[]</tt> if the Lua value was a string.</li>
     * </ul>
     *
     * @param index the index from which to get the argument.
     * @return the raw value at that index.
     * @throws IllegalArgumentException if there is no argument at that index.
     */
    Object checkAny(int index);

    /**
     * Try to get a boolean value at the specified index.
     * <p/>
     * Throws an error if there are too few arguments.
     *
     * @param index the index from which to get the argument.
     * @return the boolean value at the specified index.
     * @throws IllegalArgumentException if there is no argument at that index,
     *                                  or if the argument is not a boolean.
     */
    boolean checkBoolean(int index);

    /**
     * Try to get an integer value at the specified index.
     * <p/>
     * Throws an error if there are too few arguments.
     *
     * @param index the index from which to get the argument.
     * @return the integer value at the specified index.
     * @throws IllegalArgumentException if there is no argument at that index,
     *                                  or if the argument is not a number.
     */
    int checkInteger(int index);

    /**
     * Try to get a double value at the specified index.
     * <p/>
     * Throws an error if there are too few arguments.
     *
     * @param index the index from which to get the argument.
     * @return the double value at the specified index.
     * @throws IllegalArgumentException if there is no argument at that index,
     *                                  or if the argument is not a number.
     */
    double checkDouble(int index);

    /**
     * Try to get a string value at the specified index.
     * <p/>
     * Throws an error if there are too few arguments.
     * <p/>
     * This will actually check for a byte array and convert it to a string
     * using UTF-8 encoding.
     *
     * @param index the index from which to get the argument.
     * @return the boolean value at the specified index.
     * @throws IllegalArgumentException if there is no argument at that index,
     *                                  or if the argument is not a string.
     */
    String checkString(int index);

    /**
     * Try to get a byte array at the specified index.
     * <p/>
     * Throws an error if there are too few arguments.
     *
     * @param index the index from which to get the argument.
     * @return the byte array at the specified index.
     * @throws IllegalArgumentException if there is no argument at that index,
     *                                  or if the argument is not a byte array.
     */
    byte[] checkByteArray(int index);

    /**
     * Tests whether the argument at the specified index is a boolean value.
     * <p/>
     * This will return true if there is <em>no</em> argument at the specified
     * index, i.e. if there are too few arguments.
     *
     * @param index the index to check.
     * @return true if the argument is a boolean; false otherwise.
     */
    boolean isBoolean(int index);

    /**
     * Tests whether the argument at the specified index is an integer value.
     * <p/>
     * This will return true if there is <em>no</em> argument at the specified
     * index, i.e. if there are too few arguments.
     *
     * @param index the index to check.
     * @return true if the argument is an integer; false otherwise.
     */
    boolean isInteger(int index);

    /**
     * Tests whether the argument at the specified index is a double value.
     * <p/>
     * This will return true if there is <em>no</em> argument at the specified
     * index, i.e. if there are too few arguments.
     *
     * @param index the index to check.
     * @return true if the argument is a double; false otherwise.
     */
    boolean isDouble(int index);

    /**
     * Tests whether the argument at the specified index is a string value.
     * <p/>
     * This will return true if there is <em>no</em> argument at the specified
     * index, i.e. if there are too few arguments.
     *
     * @param index the index to check.
     * @return true if the argument is a string; false otherwise.
     */
    boolean isString(int index);

    /**
     * Tests whether the argument at the specified index is a byte array.
     * <p/>
     * This will return true if there is <em>no</em> argument at the specified
     * index, i.e. if there are too few arguments.
     *
     * @param index the index to check.
     * @return true if the argument is a byte array; false otherwise.
     */
    boolean isByteArray(int index);
}
