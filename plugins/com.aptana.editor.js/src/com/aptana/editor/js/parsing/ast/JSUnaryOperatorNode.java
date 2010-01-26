package com.aptana.editor.js.parsing.ast;

import com.aptana.editor.js.parsing.lexer.JSTokens;

public class JSUnaryOperatorNode extends JSNode
{

	protected JSUnaryOperatorNode(JSNode expression, int start, int end)
	{
		setLocation(start, end);
		setChildren(new JSNode[] { expression });
	}

	public JSUnaryOperatorNode(String operator, JSNode expression, int start, int end)
	{
		this(expression, start, end);

		short type = DEFAULT_TYPE;
		short token = JSTokens.getToken(operator);
		switch (token)
		{
			case JSTokens.DELETE:
				type = JSNodeTypes.DELETE;
				break;
			case JSTokens.EXCLAMATION:
				type = JSNodeTypes.LOGICAL_NOT;
				break;
			case JSTokens.MINUS:
				type = JSNodeTypes.NEGATIVE;
				break;
			case JSTokens.MINUS_MINUS:
				type = JSNodeTypes.PRE_DECREMENT;
				break;
			case JSTokens.PLUS:
				type = JSNodeTypes.POSITIVE;
				break;
			case JSTokens.PLUS_PLUS:
				type = JSNodeTypes.PRE_INCREMENT;
				break;
			case JSTokens.TILDE:
				type = JSNodeTypes.BITWISE_NOT;
				break;
			case JSTokens.TYPEOF:
				type = JSNodeTypes.TYPEOF;
				break;
			case JSTokens.VOID:
				type = JSNodeTypes.VOID;
				break;
		}
		setType(type);
	}

	public JSUnaryOperatorNode(short type, JSNode expression, int start, int end)
	{
		this(expression, start, end);
		setType(type);
	}

	@Override
	public String toString()
	{
		StringBuilder text = new StringBuilder();
		JSNode expression = (JSNode) getChildren()[0];
		String operator = ""; //$NON-NLS-1$
		int type = getType();
		if (type == JSNodeTypes.GROUP)
		{
			text.append("(").append(expression).append(")"); //$NON-NLS-1$//$NON-NLS-2$
		}
		else
		{
			switch (type)
			{
				case JSNodeTypes.DELETE:
					operator = "delete "; //$NON-NLS-1$
					break;
				case JSNodeTypes.LOGICAL_NOT:
					operator = "!"; //$NON-NLS-1$
					break;
				case JSNodeTypes.NEGATIVE:
					operator = "-"; //$NON-NLS-1$
					break;
				case JSNodeTypes.PRE_DECREMENT:
					operator = "--"; //$NON-NLS-1$
					break;
				case JSNodeTypes.POSITIVE:
					operator = "+"; //$NON-NLS-1$
					break;
				case JSNodeTypes.PRE_INCREMENT:
					operator = "++"; //$NON-NLS-1$
					break;
				case JSNodeTypes.BITWISE_NOT:
					operator = "~"; //$NON-NLS-1$
					break;
				case JSNodeTypes.TYPEOF:
					operator = "typeof"; //$NON-NLS-1$
					if (expression.getType() != JSNodeTypes.GROUP)
					{
						operator += " "; //$NON-NLS-1$
					}
					break;
				case JSNodeTypes.VOID:
					operator = "void "; //$NON-NLS-1$
					break;
				case JSNodeTypes.THROW:
					operator = "throw "; //$NON-NLS-1$
					break;
				case JSNodeTypes.RETURN:
					operator = "return"; //$NON-NLS-1$
					if (!expression.isEmpty())
					{
						operator += " "; //$NON-NLS-1$
					}
					break;
			}
			text.append(operator).append(expression);
		}

		return appendSemicolon(text.toString());
	}
}
