package com.itangcent.idea.utils

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.itangcent.common.constant.Attrs
import com.itangcent.common.logger.Log
import com.itangcent.common.logger.traceWarn
import com.itangcent.common.utils.*
import com.itangcent.idea.plugin.api.export.AdditionalField
import com.itangcent.idea.plugin.api.export.core.ClassExportRuleKeys
import com.itangcent.idea.plugin.settings.EventRecords
import com.itangcent.intellij.config.rule.computer
import com.itangcent.intellij.extend.toPrettyString
import com.itangcent.intellij.jvm.PsiExpressionResolver
import com.itangcent.intellij.jvm.duck.DuckType
import com.itangcent.intellij.jvm.element.ExplicitClass
import com.itangcent.intellij.jvm.element.ExplicitElement
import com.itangcent.intellij.jvm.element.ExplicitField
import com.itangcent.intellij.psi.ObjectHolder
import com.itangcent.intellij.psi.ResolveContext
import com.itangcent.intellij.psi.getOrResolve

/**
 * support rules:
 * 1. field.required
 * 2. field.default.value
 */
open class CustomizedPsiClassHelper : ContextualPsiClassHelper() {

    @Inject
    private lateinit var psiExpressionResolver: PsiExpressionResolver

    override fun afterParseFieldOrMethod(
        fieldName: String,
        fieldType: DuckType,
        fieldOrMethod: ExplicitElement<*>,
        resourcePsiClass: ExplicitClass,
        resolveContext: ResolveContext,
        kv: KV<String, Any?>,
    ) {
        //compute `field.required`
        ruleComputer.computer(ClassExportRuleKeys.FIELD_REQUIRED, fieldOrMethod)?.let { required ->
            kv.sub(Attrs.REQUIRED_ATTR)[fieldName] = required
        }

        //compute `field.default.value`
        val defaultValue = ruleComputer.computer(ClassExportRuleKeys.FIELD_DEFAULT_VALUE, fieldOrMethod)
        if (defaultValue.isNullOrEmpty()) {
            if (fieldOrMethod is ExplicitField) {
                fieldOrMethod.psi().initializer?.let { psiExpressionResolver.process(it) }?.toPrettyString()
                    ?.let { kv.sub(Attrs.DEFAULT_VALUE_ATTR)[fieldName] = it }
            }
        } else {
            kv.sub(Attrs.DEFAULT_VALUE_ATTR)[fieldName] = defaultValue
            populateFieldValue(fieldName, fieldType, kv, defaultValue)
        }

        super.afterParseFieldOrMethod(fieldName, fieldType, fieldOrMethod, resourcePsiClass, resolveContext, kv)
    }

    override fun resolveAdditionalField(
        additionalField: AdditionalField,
        context: PsiElement,
        resolveContext: ResolveContext,
        kv: KV<String, Any?>,
    ) {
        super.resolveAdditionalField(additionalField, context, resolveContext, kv)
        val fieldName = additionalField.name!!
        kv.sub(Attrs.REQUIRED_ATTR)[fieldName] = additionalField.required
        kv.sub(Attrs.DEFAULT_VALUE_ATTR)[fieldName] = additionalField.defaultValue
    }

    protected fun populateFieldValue(fieldName: String, fieldType: DuckType, kv: KV<String, Any?>, valueText: String) {
        val obj = try {
            GsonExUtils.fromJson<Any>(valueText)
        } catch (e: Exception) {
            LOG.traceWarn("failed parse json:\n$valueText\n", e)
            return
        }
        if (obj.isOriginal()) {
            return
        }
        var oldValue = kv[fieldName]
        if (oldValue is ObjectHolder) {
            oldValue = oldValue.getOrResolve()
        }
        if (oldValue == obj) {
            return
        }
        if (oldValue.isOriginal()) {
            kv[fieldName] = obj
        } else {
            kv[fieldName] = oldValue.copy()
            kv.merge(fieldName, obj)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun resolveEnumOrStatic(
        context: PsiElement,
        cls: PsiClass?,
        property: String?,
        defaultPropertyName: String,
        valueTypeHandle: ((DuckType) -> Unit)?,
    ): java.util.ArrayList<java.util.HashMap<String, Any?>>? {
        EventRecords.record(EventRecords.ENUM_RESOLVE)
        return super.resolveEnumOrStatic(context, cls, property, defaultPropertyName, valueTypeHandle)
    }

    override fun ignoreField(psiField: PsiField): Boolean {
        if (configReader.first("ignore_static_and_final")?.asBool() == false) {
            return false
        }
        return super.ignoreField(psiField)
    }

    companion object : Log()
}