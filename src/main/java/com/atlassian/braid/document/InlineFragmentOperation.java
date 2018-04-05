package com.atlassian.braid.document;

import graphql.language.InlineFragment;

final class InlineFragmentOperation extends AbstractTypeOperation<InlineFragment> {

    InlineFragmentOperation() {
        super(InlineFragment.class);
    }
}
