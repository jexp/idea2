package com.siyeh.igtest.abstraction.weaken_type;

import com.siyeh.igtest.abstraction.weaken_type.sub.NumberAdderExtension;


public class NumberAdderDemo {

    public static void main(final String[] args) {
        final NumberAdderExtension adder = new NumberAdderExtension();

        final int numberOne = adder.getNumberOne();
        System.out.println(numberOne);
    }

}