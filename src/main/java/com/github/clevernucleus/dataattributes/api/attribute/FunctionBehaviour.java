package com.github.clevernucleus.dataattributes.api.attribute;

public enum FunctionBehaviour {
	ADD((byte)0),
	MULTIPLY((byte)1);

	private final byte id;

	private FunctionBehaviour(final byte id) {
		this.id = id;
	}
	
	public static FunctionBehaviour of(final byte id) {
		return switch(id) {
			case 0 -> ADD;
			case 1 -> MULTIPLY;
			default -> ADD;
		};
	}
	
	public byte id() {
		return this.id;
	}

	@Override
	public String toString() {
		return String.valueOf(this.id);
	}
}
