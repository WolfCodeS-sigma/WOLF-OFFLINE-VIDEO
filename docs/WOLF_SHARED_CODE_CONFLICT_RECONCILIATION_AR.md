# WOLF OFFLINE VIDEO — تقرير تسوية تعارضات الكود المشترك

**تاريخ المراجعة:** 23 نوفمبر 2026  
**المستودع:** WolfCodeS-sigma/WOLF-OFFLINE-VIDEO  
**نوع العمل:** مراجعة كود ومقارنة معمارية؛ لا يتضمن تعديل كود التطبيق أو دمج فروع.

## 1. المراجع التي تمت مراجعتها

تمت مراجعة ملفات المصدر من المراجع التالية، لا من أسماء الفروع وحدها:

| المرجع | Commit |
|---|---|
| main | a955907e4870de578b064249ab38e22953e9de52 |
| Phase 18 / PR #18 | 9d5a533e7e7cce6b860bd481a2da3d55da04c301 |
| Stage 13 / PR #34 | e65dcdfe4236ba3ae556022cfb518f508ca77bbc |

رأس PR #34 وقت المراجعة ما زال مفتوحًا وDraft، ويستهدف design/stage-12-final-engine-roles. لم يظهر تشغيل GitHub Actions مرتبط بالرأس e65dcdfe في استعلام التشغيلات. لذلك لا تُنقل إليه نتائج CI لنسخ أقدم.

## 2. النتيجة التنفيذية

المساران لا يمثلان نسختين متطابقتين من التطبيق. Phase 18 يحمل منظومة كتابة القصص والمكتبة والتسجيل المجزأ وإنتاج الفيلم الطويل والصوت، بينما Stage 13 يحمل StoryDirector المتقدم، وسياسة أمان الصور، وAsset Registry وQuality Gate، وتعاون المولدات، وPOCs للمحركات، وعقد المشهد والحركة.

**القرار العام:** لا نستخدم استبدال الملفات كاملة من أحد المسارين. يجب تكوين خط إنتاج موحد يستفيد من تخطيط Stage ويحتفظ بخدمات Phase المستقلة، مع تطبيق قفل الأمان الخاص بالصور والأصول على جميع نقاط الدخول. لا تُفعّل أي ميزة رسومية محجوبة لمجرد أن كودها موجود في الفرع.

تمت مراجعة نقاط التداخل والواجهات الرئيسية في الملفات الاثني عشر التي ظهرت في تدقيق التباعد، مع مراجعة الملفات التابعة اللازمة لفهم سلوكها. هذا التقرير يحدد شكل الدمج المقبول والاختبارات المطلوبة؛ ولا يعتبر تنفيذ الدمج أو اجتياز اختبارات الرأس النهائي.

## 3. مصفوفة القرار للملفات الاثني عشر

| الملف | الاختلاف المحوري | القرار الهندسي |
|---|---|---|
| OfflineProductionEngine.kt | Phase pipeline مستقل للصور والفصول؛ Stage StoryDirector وsafe fallback وتوقيت آخر | توحيدهما حول StoryDirector للتخطيط وSceneRenderPipeline لاختيار backend، مع حفظ الصورة المختارة وعدم استبدالها |
| WolfImageDepthSegmenter.kt | Phase تحليل محدود الذاكرة و8-connectivity وتنظيف آمن؛ Stage semantic gate مغلق | اعتماد خوارزمية Phase المحدودة والاختبارات، وإبقاء safeForParallax من Stage شرطًا إلزاميًا |
| MainActivity.kt | Phase Story AI والمكتبة والتسجيل المجزأ؛ Stage زر Filament Debug | الاحتفاظ بواجهة Phase والتسجيل المجزأ؛ إخراج POC Filament من تطبيق الإنتاج |
| app/build.gradle.kts | Phase Offline Runtime Guard وLiteRT-LM؛ Stage Filament main dependencies وABI/noCompress | حفظ Offline Guard وLiteRT؛ عزل Filament خارج وحدة الإنتاج أو ضمن وحدة POC مستقلة |
| WolfAudioMixer.kt | Phase noise gate وhigh-pass وducking وoffset/limit؛ Stage ملء كامل المدة بالصمت | دمج معالجة Phase مع zero-padding من Stage ومع توقيت مقدمة/راوي موحد |
| WolfProceduralMotionEngine.kt | Phase يرسم شبحًا وطبقات أشياء إضافية؛ Stage يمنع أشكال البشر والمخلوقات | إبقاء مؤثرات Stage البيئية فقط في مسار الإنتاج الحالي |
| WolfSceneArtEngine.kt | Phase يرسم بيئات وأشكالًا وظلالًا/شخصيات إجرائية؛ Stage خلفية جوية فقط | اعتماد Stage كـfallback نشط حتى اعتماد أصول واقعية مرخصة |
| WolfSceneDepthComposer.kt | Phase يحفظ حالة Canvas في finally؛ Stage يضيف semantic gate وفحص bitmap | دمج finally من Phase مع safeForParallax وفحوص سلامة Stage |
| OfflineStorageEstimateTest.kt | Phase يحسب 4 نسخ فيديو أثناء الفصل؛ Stage يحسب 3 | استخدام 4 عند الحفاظ على ملفات اللقطات المحفوظة؛ إثبات الرقم باختبار دورة الملفات |
| WolfAudioMixerTest.kt | Phase اختبارات المعالجة الصوتية؛ Stage اختبار طول PCM عند انتهاء المصادر مبكرًا | الاحتفاظ بجميع اختبارات DSP وإضافة/الإبقاء على اختبار ملء المدة |
| WolfImageDepthSegmenterTest.kt | Phase تغطية صور وشفافية وضوضاء وحدود كثيرة؛ Stage semantic-lock | دمج الاختبارات مع اختبار صريح أن التحليل اللوني لا يفعّل Parallax |
| README.md | Phase يوثق Story AI والمكتبة والصوت؛ Stage يوثق Registry والمحركات والعقد | توثيق النظامين مع تمييز POC عن الإنتاج والمولدات PENDING عن الجاهزة |

التفصيل الفني لكل قرار أدناه.

## 4. OfflineProductionEngine.kt — نقطة الدمج الأساسية

### 4.1 مسؤوليات Phase التي يجب أن تبقى

- يستخدم WolfSceneRenderPipeline لتجهيز اللقطة واختيار مسار الصورة أو المشهد الإجرائي.
- يفصل تحليل الصورة المرفوعة عن تركيبها وعرضها.
- يحتوي مسار الإنتاج الطويل على ملفات لقطات مستقلة، ثم ملفات فصول، ثم ملف نهائي.
- يتحقق من عدد إطارات ملف اللقطة المحفوظ قبل إعادة استخدامه، ولا يكتفي بوجود الملف أو حجمه.
- يدمج ملفات الفصول مع التحقق من عدد الإطارات المتوقع.
- يربط إنتاج الفيلم مع التسجيل المعتمد ومقدمة WOLF والصوت والمؤثرات.

### 4.2 مسؤوليات Stage التي يجب أن تبقى

- StoryDirectorEngine هو مصدر تقسيم القصة إلى مشاهد وخطط لقطات ونية كاميرا وتكوين وإضاءة.
- يحتفظ كل مقطع لقطة بإزاحته داخل اللقطة الأصلية وعدد إطارات اللقطة الكلي؛ فلا تبدأ الحركة من الصفر عند عبور حد الفصل.
- يستدعي تنظيف الملفات المؤقتة القديمة التابعة لـWOLF قبل فحص مساحة التخزين.
- إذا اختار المستخدم صورة وتعذر فك ترميزها، يفشل المشهد برسالة واضحة.
- إذا أمكن فك الصورة لكن لم يتوفر فصل دلالي موثوق، يستخدم الصورة الأصلية دون حركة كاميرا أو Parallax.
- لا يسمح لـconfidence اللوني وحده بفتح حركة طبقات العمق.
- يحمل بصمة إصدار للرندر حتى لا يعاد استخدام فصل محفوظ من خطة قديمة.

### 4.3 عقد اختيار مسار الصورة المقترح

يجب أن تكون الحالات منفصلة بوضوح:

1. **لم يختر المستخدم صورة لهذا المشهد:** يمكن استخدام مسار المشهد الإجرائي وفق سياسة Stage. لا يجوز اختراع إنسان أو مخلوق أو مبنى أو Prop يبدو واقعيًا قبل وجود أصل معتمد.
2. **اختار المستخدم صورة وتم فك ترميزها بنجاح، لكن التقسيم فشل أو كان غامضًا:** يجب الاحتفاظ بالصورة الأصلية كما هي، مع قص ملائم للأبعاد فقط، دون حركة كاميرا أو Zoom أو Parallax أو تحريك طبقات.
3. **اختار المستخدم صورة وفشل فك ترميزها:** يجب إظهار خطأ صريح للمشهد. لا يجوز التحول تلقائيًا إلى مشهد آخر وكأن صورة المستخدم لم تكن محددة.
4. **التقسيم اللوني أعطى طبقات مرشحة دون تحقق دلالي:** يعامل مثل حالة الصورة الثابتة؛ لا يستخدم كدليل كافٍ لتحريك العمق.
5. **توجد طبقات اجتازت تحققًا دلاليًا فعليًا ومقيدة بنطاق استخدام معتمد:** يمكن وقتها استخدام محرك 2.5D والـParallax، بشرط تمرير شرط الأمان نفسه من المحرك إلى Composer.

هذا العقد يحافظ على fallback الذي لا يوقف الفيلم عند غياب صورة، لكنه يمنع استبدال صورة اختارها المستخدم أو تشويهها.

### 4.4 دمج StoryDirector مع SceneRenderPipeline

التركيب المقترح ليس استبدال واحد بالآخر:
- StoryDirector مسؤول عن خطة المشهد واللقطة وعدد الإطارات والموضع داخل اللقطة والحركة والتكوين والإضاءة.
- SceneRenderPipeline مسؤول عن تحديد backend وتنفيذ مسار الصورة أو المسار الإجرائي.
- UploadedImageEngine مسؤول عن الصورة الأصلية والطبقات إن كانت آمنة.
- SceneArtEngine النشط حاليًا يظل fallback جويًا محافظًا.
- ProceduralMotionEngine يضيف مؤثرات بيئية فقط؛ ولا يغير ملكية StoryDirector لخطة اللقطة.

يجب أن تعمل اختبارات StoryDirector على خط الإنتاج نفسه، لا أن تكون مكتبة تخطيط منفصلة لا تصل إلى renderer.

### 4.5 الاستئناف وملفات اللقطات

Phase يحفظ ملف MP4 لكل مقطع لقطة، ويتحقق من عدد إطاراته، ثم يدمج ملفات اللقطات إلى فصل ويحتفظ بملفات الفصول لإعادة الاستخدام. Stage يحفظ ملف الفصل ويستخدم sliceShotSegments لتمرير تقدم الحركة الصحيح، لكنه لا يحفظ ملفًا منفصلًا لكل لقطة في مسار الفصل الحالي.

القرار المقترح هو الحفاظ على:
- تقطيع StoryDirector إلى مقاطع لقطات دقيقة.
- بيانات startOffsetFrames وtotalShotFrames من Stage.
- حفظ ملف لكل مقطع لقطة وإعادة استخدامه فقط بعد التحقق من عدد إطاراته من Phase.
- التحقق من العدد الإجمالي عند دمج اللقطات إلى فصل، وعند دمج الفصول إلى الفيديو النهائي.
- عدم بدء الحركة من تقدم صفر في لقطة تم استكمالها من منتصفها.

وبذلك نحفظ ميزة Phase14 الخاصة بإعادة تصدير اللقطة الفاشلة وحدها، دون خسارة دقة StoryDirector.

## 5. مدة الفيلم والتعليق الصوتي والمقدمة

### 5.1 الاختلاف

Phase يحتوي WolfCinematicAudioTimeline. عند اعتماد الراوي:
- يقيس مدة التسجيل المعتمدة.
- يجعل التسجيل هو مدة جسم الفيلم.
- يضع المقدمة قبل الراوي.
- يبدأ الراوي عند narrationOffsetUs بعد المقدمة.
- يكون إجمالي الفيديو هو المقدمة + مدة الراوي.
- يرفض التسجيل الذي يتجاوز حد الفيلم المسموح بعد إضافة المقدمة.

Stage يوثق قرارًا معتمدًا بأن مدة الراوي المعتمد هي المرجع، وأن المشاهد تتمدد لتغطي التسجيل. لكن buildAudioTimeline في Stage يعيد مدة الراوي نفسها كمدة الفيديو ولا يضيف مدة المقدمة، كما أن استدعاء Mixer لا يمرر إزاحة بداية الراوي.

### 5.2 القرار المقترح

اعتماد قاعدة Stage12: لا يُقص تسجيل الراوي المعتمد، ومدته المقاسة هي مرجع مدة جسم الفيلم. ثم الاحتفاظ بآلية Phase لتوقيت المقدمة:
- المقدمة تسبق التعليق.
- مدة الفيديو الكلية = مدة المقدمة + مدة الراوي.
- تبدأ عينات الراوي بعد المقدمة.
- تظل المؤثرات الصوتية على خط الفيلم الكامل.
- عند تجاوز الحد الأقصى مع المقدمة، يعرض التطبيق خطأً واضحًا بدل قطع التسجيل أو تجاوز قيود الإنتاج.

عند عدم وجود تسجيل معتمد، تظل مدة المشروع المحددة هي مرجع الفيلم، وتكون المقدمة جزءًا من هذه المدة وفق إعداد المستخدم.

### 5.3 منع الحساب المزدوج

يجب أن تكون دالة واحدة مسؤولة عن حساب مدة المقدمة، مدة جسم الفيلم، عدد الإطارات، إزاحة الراوي وحده. لا يحسب MainActivity مدة الراوي ويضيف المقدمة ثم يعيد المحرك حسابهما بطريقة مختلفة. يمرر MainActivity مدة المشروع وبيانات الراوي المعتمد فقط، ويستخرج المحرك Timeline موحدًا.

كما يجب اختبار:
- فيلم محدد 30 ثانية دون راوي.
- راوي معتمد مدته 7 ثوانٍ دون مقدمة.
- راوي معتمد مدته 7 ثوانٍ مع مقدمة 3 ثوانٍ.
- راوي أطول من المدة التي اختارها المستخدم.
- مدة راوي تتجاوز الحد الأقصى مع المقدمة.
- عدم وجود راوي مع مؤثرات صوتية فقط.
- عدم فقد الإطارات أو تغيير مدة الفيديو عند قصر الصوت.

## 6. WolfImageDepthSegmenter.kt — خوارزمية التحليل مقابل قفل الدلالة

### 6.1 ما في Phase

- حد أقصى لطول الضلع 768 بكسل.
- حد إجمالي للتحليل 262,144 بكسل.
- تصفية الشفافية قبل عدّ المقدمة.
- flood fill وconnected components بثمانية اتجاهات.
- إحصاءات حدود أكثر متانة.
- تجاهل المكونات الصغيرة والضوضاء.
- إذا وقع فشل أثناء إنشاء الطبقات، تعاد تدوير كل الصور التي أُنشئت جزئيًا.
- تستخدم finally لتنظيف bitmap التحليل المصغر.
- تغطية اختبارات تشمل التدرج الخفيف، ضوضاء الحدود، الثقوب الشفافة، الضوضاء الصغيرة، الصورة الموحدة، والصورة التي تملأ الإطار تقريبًا.

### 6.2 ما في Stage

- يحتوي Layers على heuristicCandidateSeparated وsemanticValidationPassed وsafeForParallax.
- semanticValidationPassed يظل false حاليًا.
- isSemanticSegmentationReady() ترجع false.
- هذا يمنع استعمال التحليل اللوني وحده في Parallax؛ يجب الحفاظ على هذا القفل.

### 6.3 الخلل الذي ظهر في مراجعة التنفيذ

وثائق مرحلة التقسيم تتحدث عن 8-connected flood fill، لكن الكود الفعلي في Stage يستخدم أربعة اتجاهات فقط في توصيل الخلفية والمكونات: أعلى وأسفل ويمين ويسار. كما أن تكوين candidate في Stage لا يستبعد alpha المنخفض قبل تصنيف اللون، بخلاف Phase.

إذن لا ينبغي أخذ خوارزمية Stage الحالية كما هي لمجرد وجود semantic gate فيها.

### 6.4 القرار النهائي لهذا الملف

- أخذ خوارزمية Phase ذات حد البكسلات والشفافية و8-connectivity والتنظيف الآمن.
- إدخال ناتجها في Layer model الخاص بـStage.
- تعيين semanticValidationPassed إلى false إلى أن يوجد تحقق دلالي حقيقي.
- جعل safeForParallax هو الشرط الوحيد لقبول الطبقات في كل مستهلك.
- لا يسمح للـconfidence أو separated أو heuristicCandidateSeparated بفتح حركة العمق.
- الحفاظ على attemptSegmentation كاحتواء لاستثناءات التحليل، مع معالجة OutOfMemoryError بصورة منفصلة وعدم ابتلاعها ثم متابعة عمليات قد تزيد استهلاك الذاكرة.

## 7. حماية قفل الصور عبر كل مسارات Phase

ظهرت نقطة تجاوز مهمة في Phase:
- WolfUploadedImageEngine.prepare يقبل الطبقات بالاعتماد على separated وconfidence.
- Wolf25DCinematicImageEngine يستخدم النتيجة المجهزة.
- WolfSceneRenderPipeline يستطيع تحويل صورة غير قابلة للتحليل إلى backend إجرائي.

بالتالي فإن إضافة safeForParallax إلى Segmenter وحده لا يكفي. يجب تعديل جميع نقاط استهلاك الطبقات لكي تستخدم safeForParallax نفسه، بما في ذلك UploadedImageEngine و25D engine وSceneDepthComposer.

ولا يجوز أن تتحول صورة مستخدم اختارها وفشل فكها إلى مشهد إجرائي تلقائي. يُسمح بالمسار الإجرائي فقط عندما لا توجد صورة مختارة للمشهد أصلًا.

## 8. WolfSceneDepthComposer.kt

Phase أكثر اكتمالًا في حماية Canvas عبر try/finally حول save/restore. Stage أضاف:
- check(layers.safeForParallax).
- فحص أن الخلفية والطبقات ليست recycled قبل التركيب.

القرار هو جمع الاثنين:
- الاحتفاظ بـtry/finally في كل مسار canvas.save.
- الإبقاء على فحص safeForParallax قبل بدء تركيب الطبقات.
- الإبقاء على فحص صلاحية الصور الثلاث.
- لا يتم استدعاء renderImageLayers إلا بعد تحقق المحرك من safeForParallax.
- إذا لم يتحقق الشرط، يكون القرار خارج Composer هو الصورة الثابتة، وليس رمي استثناء يقطع الفيلم.

هذا يجعل Composer خط دفاع أخيرًا، لا بديلًا عن اختيار backend الصحيح.

## 9. WolfProceduralMotionEngine.kt وWolfSceneArtEngine.kt

### 9.1 الحركة

Phase أضاف drawGhost ومفردات وصفية للأشباح والأطياف، وأضاف في renderDepthAware تمرير البيئة وplannedLayers ورسم عناصر مستقلة من WolfSceneLayerSystem.

Stage أزال رسم الشخصيات والمخلوقات من محرك الحركة، وأبقى:
- الضباب.
- الجسيمات.
- الأغصان.
- المطر.
- نبضات بيئية خفيفة.
- تشويش الإشارة.

**القرار:** اعتماد نسخة Stage البيئية في مسار الإنتاج الحالي. لا يتم تفعيل ghost أو silhouette أو طبقات بشرية/مخلوقات إجرائية من Phase حتى تُراجع سياسة الأصول الواقعية ويصبح لدينا أصل معتمد أو نظام حركة مسموح ومثبت.

### 9.2 SceneArt

Phase يرسم غابة وممرًا ونافذة وبابًا ومقبرة وغرفة ومنزلًا مسكونًا، إضافة إلى قمر أحمر ونجوم وظلال وأشكال بشرية/أشباح إجرائية، كما يرسم شريط تقدم داخل الإطار.

Stage حوّل المحرك إلى خلفية جوية فقط:
- تدرج داكن.
- مسارات ضباب.
- Vignette.
- دون مبانٍ أو Props أو شخصيات أو أشباح هندسية.

هذا القرار متوافق مع Stage27 وسياسة جودة الأصول التي تمنع تقديم رسوم هندسية بدائية على أنها أصول سينمائية واقعية.

**القرار:** تبقى نسخة Stage هي الـfallback النشط. يمكن الاحتفاظ بملفات Phase في المستودع التاريخي والتطويري، لكن لا يتم توصيل رسم الشخصيات والمباني الإجرائي إلى مسار إنتاج المستخدم قبل اعتماد أصول حقيقية ومراجعة سياسة الاستخدام.

كما يجب إزالة أي شريط تقدم مرسوم داخل صورة الفيديو؛ تقدم العملية يجب أن يبقى في واجهة التطبيق فقط.

## 10. WolfAudioMixer.kt وWolfAudioMixerTest.kt

### 10.1 مزايا Phase

- يقبل narrationUri وeffectsFile كقيم اختيارية، فيدعم راويًا فقط أو مؤثرات فقط أو الاثنين.
- يدعم narrationOffsetUs وnarrationLimitUs.
- يزيل DC والترددات المنخفضة غير المرغوبة.
- يطبق noise gate.
- يخفض المؤثرات أثناء الكلام (ducking).
- يحتوي اختبارات لمستوى الراوي، خفض المؤثرات، noise gate، limiter، وإمكانية كتم المؤثرات.

### 10.2 ميزة Stage التي يجب ألا تضيع

في Stage، تستمر mixPcmFiles حتى الحد الزمني المطلوب، وتكتب أصفارًا بعد انتهاء مصادر الصوت. اختبار Stage يثبت أن ناتج مزج mono PCM لمدة ثانية بمعدل 22,050 عينة في الثانية يبلغ 44,100 بايت حتى إذا انتهت المصادر بعد جزء صغير من المدة.

في Phase يوجد شرط يكسر الحلقة عندما تكون قراءة الراوي والمؤثرات صفرًا معًا. هذا قد يجعل PCM أقصر من خط الفيديو، وهو تراجع لا ينبغي إدخاله.

### 10.3 قرار الدمج

اعتماد API ومعالجة Phase، مع استعادة zero-padding من Stage. يجب عدم إنهاء ملف PCM قبل نهاية maxDurationUs لمجرد انتهاء الملفات المصدرية. إذا انتهت العينات مبكرًا، تستمر بقية الخط بصمت. وتضاف اختبارات تجمع:
- صوت الراوي والمؤثرات.
- الراوي وحده.
- مؤثرات وحدها.
- offset بعد المقدمة.
- حد مدة الراوي.
- استمرار PCM حتى المدة النهائية.
- استمرار ducking وnoise gate دون تغيير مدة الفيديو.

## 11. app/build.gradle.kts — تعارض حتمي مع Offline Guard

### 11.1 Phase

يوفر:
- LiteRT-LM Android 0.18.0.
- مهمة verifyOfflineRuntime التي تفحص Manifest الأصلي والمجمع.
- كشف أذونات الإنترنت وحالة الشبكة والـcleartext وWebView وواجهات الشبكة الشائعة وروابط HTTP(S).
- allowlist للتبعيات المباشرة، حاليًا LiteRT-LM فقط.

### 11.2 Stage / Filament PR #32

يضيف ثلاثة dependencies مباشرة في app/build.gradle.kts:
- filament-android 1.75.0
- gltfio-android 1.75.0
- filament-utils-android 1.75.0

ويضيف ABI filters وnoCompress للملفات الخاصة بـFilament وGLB.

### 11.3 التعارض

عند جمع الملفين كما هما، فإن verifyOfflineRuntime سيرفض اعتماديات Filament؛ لأنها غير موجودة في allowlist. وإخفاء زر Filament عبر FLAG_DEBUGGABLE لا يعزل المكتبات عن Release APK، لأن dependencies معلنة على مستوى app implementation وملف HorrorVideoExporter موجود في مصدر التطبيق الأساسي.

### 11.4 القرار

- الاحتفاظ بـverifyOfflineRuntime وبـLiteRT-LM.
- إبقاء POC محرك Filament في engine-pocs حيث يوجد بالفعل مسار اختبار مستقل.
- لا تُضاف تبعيات Filament إلى إنتاج app لمجرد نجاح POC خمس ثوانٍ.
- إذا احتاج الفريق في مرحلة لاحقة إلى إدخال Filament إنتاجيًا، يجب أن يكون ذلك قرارًا مستقلًا مدعومًا بقياس حجم Release APK وABI وموارد الجهاز واختبار خط الإنتاج الحقيقي، ثم تُحدّث بوابة الاعتماديات بموافقة صريحة.
- لا نلغي Offline Guard ولا نسمح باعتماديات إضافية واسعة لتجاوز الفحص.

## 12. MainActivity.kt — توحيد واجهة المنتج

### 12.1 وظائف Phase التي يجب الحفاظ عليها

- شاشة WOLF OFFLINE STORY AI.
- الوصول إلى مكتبة WOLF HORROR KNOWLEDGE LIBRARY.
- إضافة القصة الحالية إلى المكتبة.
- تسجيل الراوي على أجزاء مستقلة.
- استيراد التسجيل السابق واستكماله بدل فقده.
- حالات voiceRecoveryPending وvoiceMergePending وقفل العمليات المتعارضة.
- ربط التسجيل ببصمة نص القصة المعتمد.
- معاينة التسجيل واعتماده وإعادة تسجيله.

### 12.2 ما وجد في Stage

- أضاف زر Filament داخل MainActivity لا يظهر إلا في Debug.
- أضاف قفلًا بين اختبار Filament والإنتاج العادي.
- يستخدم تدفق تسجيل صوتي أبسط يبدأ تسجيلًا جديدًا ولا يحتوي منطق استيراد الجزء السابق واستكماله الموجود في Phase.

### 12.3 صلاحية الميكروفون

في كلا الرأسين تمت مراجعة:
- RECORD_AUDIO في AndroidManifest.xml.
- checkSelfPermission عند بدء التسجيل.
- requestPermissions عند غياب الإذن.
- onRequestPermissionsResult للتعامل مع المنح والرفض.

إذن الكود الذي تمت مراجعته يحتوي مسار طلب إذن ميكروفون. لكن ذلك لا يثبت ظهور نافذة الإذن على الهاتف الفعلي أو عمل زر التسجيل في كل الحالات؛ يلزم اختبار Android UI/Instrumentation يختبر الحالة غير الممنوحة ثم المنح والرفض، ويتبعه قبول فعلي على Realme 7.

### 12.4 القرار

- الحفاظ على واجهة Phase الخاصة بـStory AI والمكتبة والتسجيل المجزأ.
- الاحتفاظ بتدفق طلب الإذن ونتيجته مع اختبار تشغيل فعلي.
- نقل واجهة Filament إلى وحدة POC منفصلة، وعدم إدخال زرها أو exporter في MainActivity الإنتاجي.
- لا يُسمح لتجربة محرك أن تمنع أو تتداخل مع عملية الإنتاج الأساسية بعد نقلها إلى وحدة مستقلة.

## 13. OfflineStorageEstimateTest.kt — أربع نسخ مقابل ثلاث

Phase يحسب أربع نسخ فيديو متداخلة أثناء إنتاج الفيلم المجزأ: ملفات مقاطع اللقطات، ملفات الفصول، الملف المدمج الوسيط، ونسخة التسليم النهائية.

Stage يحسب ثلاث نسخ لأنه في تنفيذه الحالي يحتفظ بملفات الفصول ثم ينتج ملفًا نهائيًا، دون حفظ ملفات MP4 منفصلة لكل مقطع لقطة.

وبما أن القرار المقترح يحافظ على ميزة Phase الخاصة بملفات اللقطات وإعادة تصدير اللقطة الفاشلة فقط، فالتقدير الأولي الصحيح يجب أن يبقى أربع نسخ عند استخدام هذا المسار. لا نخفضه إلى ثلاث لمجرد أن Stage استخدم مسارًا أقل في عدد الملفات.

يجب أن يغطي الاختبار دورة الإنتاج الفعلية، ويتأكد من أن تقدير المساحة يغطي تداخل الملفات والنسخ عند التصدير والحفظ، وألا يبالغ أو يقلل بناءً على افتراض غير مقاس.

## 14. WolfImageDepthSegmenterTest.kt — تغطية مشتركة

الاختبارات الموسعة في Phase تغطي:
- فشل segmentation مع fallback وإبلاغ الخطأ.
- نجاح segmentation.
- فصل foreground عن الخلفية.
- اختلاف المكونات المنفصلة.
- الصورة الموحدة.
- الحتمية.
- التدرج البسيط وفوضى الحدود.
- الثقوب الشفافة.
- الضوضاء الصغيرة والمبعثرة.
- رفض foreground الذي يستهلك معظم الإطار.

Stage أضاف الاختبار الأهم من ناحية السياسة:
- Color heuristic never claims semantic segmentation is ready.

**القرار:** جمع تغطية Phase الواسعة مع اختبارات Stage التي تثبت أن heuristicCandidateSeparated لا يساوي semanticValidationPassed، وأن safeForParallax يظل false ما لم يوجد محقق دلالي معتمد. تضاف حالات تثبت أن أي مستهلك لا يستطيع تجاوز القفل.

## 15. README.md — توثيق موحد دون ادعاءات زائدة

يجب أن يجمع README بعد الاستعادة بين:
- اتجاه المنتج ومسار الإنتاج Offline.
- Story AI واشتراط استيراد نموذج .litertlm متوافق يدويًا.
- مكتبة الرعب المحلية ومراجعة الأصالة.
- التسجيل المجزأ وربطه بنص القصة المعتمد.
- StoryDirector وإدارة اللقطات والفصول والاستئناف.
- سياسة الصور الآمنة وAsset Registry وQuality Gate.
- تصميم WPGL وتعاون المولدات مع توضيح أن الميزانيات الإنتاجية ما زالت PENDING حتى القياس.
- POCs المحركات وأدوارها وحدود الإثبات.
- سياسة الاختبارات وOffline runtime guard.

ويجب ألا يوحي README بأن:
- Cocos اجتاز الاختبار؛ فهو BLOCKED — NOT PASSED.
- Filament أصبح محرك الإنتاج الرئيسي؛ هو POC مستقل.
- مولدات Stage10 جاهزة للإنتاج مع أن حدود الموارد الفعلية PENDING.
- توجد مكتبة أصول سينمائية APPROVED كاملة بينما سجل الإنتاج بدأ فارغًا.
- الرسوم الإجرائية للأشباح والمباني أصبحت fallback إنتاجيًا؛ Stage27 أغلق هذا الاتجاه.

## 16. ملفات تابعة يجب أن تدخل في خطة الدمج

هذه الملفات ليست جميعها ضمن قائمة الاثني عشر المشتركة، لكن تجاهلها سيؤدي إلى دمج غير مكتمل:

### من Stage

- StoryDirectorEngine.kt: يحتوي خطة المشاهد واللقطات، توزيع الإطارات، sliceShotSegments، استعادة تقدم اللقطة، compositionFocusX، motionProgress، ومعالجة الإضاءة. يجب الاحتفاظ بإضافات Stage.
- WolfAssetRegistry.kt وملف manifest: يجب أن يبقى الفحص fail-closed.
- WolfProceduralGenerationLibrary.kt والمولدات: تبقى مقيدة بميزانيات PASS وقياسات موثقة، لا TEST_ONLY.
- WolfMotionCodeLibrary.kt وWolfSceneContract.kt: يبقيان تصميم/عقد منفصلًا حتى تنفيذ Adapter واعتماده.
- engine-pocs: يجب أن تبقى POCs معزولة عن APK الإنتاج.

### من Phase

- WolfCinematicAudioTimeline.kt: مصدر موحد لوقت المقدمة وبداية الراوي.
- WolfLongFilmEngine.kt: يضيف حدود اللقطات وإزاحات الاستئناف.
- WolfLongFilmCheckpointStore.kt: يحتفظ بنقاط الاستئناف.
- WolfSceneRenderPipeline.kt وWolfUploadedImageEngine.kt وWolf25DCinematicImageEngine.kt: تحفظ كطبقات معمارية، لكن يُفرض عليها قفل safeForParallax.
- WolfOfflineStoryAiEngine.kt وWolfOfflineStoryAiActivity.kt.
- WolfHorrorKnowledgeLibrary.kt وWolfHorrorKnowledgeLibraryActivity.kt.
- WolfStoryLengthStructureEngine.kt وWolfStoryOriginalityReviewEngine.kt وWolfNarrativePatternAudit.kt.
- WolfStoryVoiceEngine.kt وWolfVoiceSegmentStore.kt.

هذه المكونات الفريدة يجب أن تبقى عند إعادة توحيد التاريخ، حتى لو اختلف مكان استدعائها أو توقيت دمجها.

## 17. اختبارات القبول المطلوبة بعد إنشاء النسخة الموحدة

لا يعلن نجاح توحيد الملفات لمجرد نجاح Kotlin compile. يجب أن تنفذ مجموعة اختبارات مترابطة:

### الصور والرندر
- no image selected → procedural atmospheric path.
- user image decodes + segmentation unavailable → original still, no camera transform.
- user image selected + decode failure → visible error, no silent procedural replacement.
- heuristic layers + semantic gate false → no Parallax.
- corrupted/recycled layer bitmap → safe failure without leaking Canvas state.
- semantic gate cannot be bypassed by UploadedImageEngine or 2.5D engine.

### القصة واللقطات والفصول
- StoryDirector plan and frame budgets sum exactly to target.
- user image mapping remains tied to original shot number when the chapter begins at a later shot.
- split shot resumes from its original frame offset, not zero.
- valid saved shot reused only after exact frame count check.
- incomplete shot regenerated without rerendering valid neighboring shots.
- chapter and final merge verify exact frame totals and continuous timestamps.

### الصوت والتوقيت
- approved voice is never cut.
- intro is before narration, and narration offset equals intro duration.
- final video duration equals intro plus full approved narration when narrator timing is authoritative.
- without approved narration, project duration remains the clock.
- PCM output fills the requested duration with silence after source end.
- noise gate, high-pass, ducking and limiter remain active.

### الواجهة والأذونات
- prompt microphone permission when absent.
- record after grant; report denial without falsely starting.
- preserve earlier voice segments after adding a new segment.
- edited story invalidates old approval and previous recording association.
- Story AI/library screens remain accessible.
- Filament POC is unavailable in production app UI and dependencies.

### البناء والأمان
- :app:verifyOfflineRuntime passes.
- lintDebug and testDebugUnitTest pass.
- assembleDebug and connectedDebugAndroidTest pass.
- no Filament POC native dependency is packaged in Release unless independently approved.
- workflow queue prevents simultaneous runs from canceling one another.
- root gradle.properties changes trigger the correct workflow.
- CI status must be attached to the exact candidate head.

## 18. وضع المحركات

لم تتغير القرارات السابقة:
- Godot 4.7.2: POC معزول، ليس دمجًا إنتاجيًا.
- Filament 1.75.0: POC معزول، وليس بديلًا لمسار OfflineProductionEngine.
- libGDX وLottie وKorGE: POC evidence فقط حسب نطاق كل اختبار.
- SceneView: واجهة مبنية على Filament وليست محركًا مستقلًا.
- Cocos Creator 3.8.7: BLOCKED — NOT PASSED حتى تقديم Creator-generated Android project وإثبات rendered frame.
- Blender: أداة إعداد أصول خارج APK.

لا يُسمح بدمج محرك في الإنتاج لمجرد نجاح تغليف APK أو اختبار عينة قصيرة.

## 19. حالة CI الحالية أثناء هذه المراجعة

رأس PR #34 وقت التدقيق هو e65dcdfe4236ba3ae556022cfb518f508ca77bbc. استعلام تشغيلات Actions المرتبطة بهذا الـSHA لم يُظهر تشغيلًا. بالتالي لم أجد دليلًا حديثًا يثبت اجتياز الرأس الحالي.

يوجد تشغيل سابق على SHA مختلف e4b5255508d89b140337e6ad85a81412978b2029:
- فحوص الأصول نجحت.
- بناء Debug وLint واختبارات JVM نجحت.
- KorGE packaging نجح.
- Android app integration regression نجح.
- Job المحركات فشل في connectedGodotDebugAndroidTest برسالة Process crashed.
- السجل الذي تمت مراجعته لم يحتوِ على FATAL EXCEPTION أو SIGSEGV أو SIGABRT يحدد السبب؛ لذلك السبب الجذري للانهيار غير محسوم.

ولا تُنقل هذه النتيجة إلى الرأس الحالي.

## 20. قرارات الملفات النهائية قبل أي تنفيذ

1. OfflineProductionEngine: StoryDirector للتخطيط، Phase pipeline كتنظيم backends، Stage user-image safety، Phase narration timeline، Phase shot artifacts مع Stage shot offsets.
2. Segmenter: Phase bounded/alpha/8-connected algorithm + Stage semantic gate.
3. MainActivity: Phase production UI + segmented voice recovery + microphone permission flow; Filament POC خارج شاشة الإنتاج.
4. Gradle: Phase offline guard + LiteRT; Filament remains in isolated POC module.
5. AudioMixer: Phase DSP and offset/limit API + Stage zero-padding.
6. ProceduralMotionEngine: Stage environmental-only behavior in production.
7. SceneArtEngine: Stage atmospheric-only fallback.
8. SceneDepthComposer: Phase Canvas finally + Stage safeForParallax/recycled guards.
9. StorageEstimate: 4 video copies where shot artifacts are preserved; validate with artifact-lifecycle tests.
10. Audio tests: all Phase DSP tests plus Stage full-duration PCM test.
11. Image tests: full Phase heuristic safety suite plus Stage semantic lock tests.
12. README: combine feature inventories and state proof boundaries accurately.

## 21. سجل سلامة هذه الخطوة

- تمت مراجعة المراجع الثلاثة المحددة أعلاه.
- تمت مراجعة منطق الملفات الاثني عشر المشتركة ونقاط استدعائها الرئيسية.
- تمت مراجعة الملفات التابعة التي تحدد ملكية التخطيط والتوقيت والأصول والمحركات.
- تمت مراجعة Manifest والـpermission flow في كلا مساري التطبيق.
- لم يتم تعديل Kotlin أو Gradle أو Manifest أو Workflows.
- لم يتم إنشاء فرع استعادة.
- لم يتغير Base أو Head لأي PR.
- لم يتم دمج أو إغلاق أو حذف أي PR أو فرع.
- لم تتم إعادة كتابة تاريخ Git.
- التغيير في هذه الخطوة هو تقرير توثيقي مستقل فقط.

**انتهت مراجعة التعارضات. المستودع ما زال غير جاهز للدمج حتى تُنشأ نسخة موحدة على فرع استعادة، وتُنفذ التغييرات كاملة وتنجح اختبارات الرأس المرشح نفسه.**


## 22. قرار لاحق مرتبط بالتعليق الصوتي الآلي — WOLF-OFFLINE-TTS-001

بعد مراجعة دراسة Android TTS، أُضيفت ميزة التعليق الصوتي الآلي إلى مراجع التطبيق بوصفها **ميزة مستقلة مستقبلية**، وليست جزءًا من إصلاحات Phase 4 الحالية ولا تغييرًا على مسار التسجيل البشري.

### المراجع الملزمة

- مواصفة التنفيذ: [WOLF_OFFLINE_VIDEO_OFFLINE_TTS_IMPLEMENTATION_SPEC_AR.md](WOLF_OFFLINE_VIDEO_OFFLINE_TTS_IMPLEMENTATION_SPEC_AR.md).
- الدراسة الأصلية المنقولة كما أُرسلت: [WOLF_OFFLINE_VIDEO_Offline_TTS_Feasibility_Arabic.txt](reference/WOLF_OFFLINE_VIDEO_Offline_TTS_Feasibility_Arabic.txt).
- معرّف الميزة الثابت: `WOLF-OFFLINE-TTS-001`.

### قرارات الصوت

1. التنفيذ المستقبلي Native Android Kotlin باستخدام `TextToSpeech`؛ لا نقل Capacitor أو WebView أو JavaScript، ولا خدمة سحابية.
2. التسجيل البشري يظل مسارًا مستقلًا مع الحفاظ على التسجيل المجزأ والاستعادة والاعتماد الحالي. الصوت الآلي لا يستخدم الميكروفون ولا يطلب `RECORD_AUDIO`.
3. اختيار Google Speech Services أولًا إن كان مثبتًا، ثم تجربة المحرك الافتراضي، لكن لا يُقبل أي محرك إلا بشروط الأوفلاين نفسها.
4. الصوت المعتمد يجب أن يثبت `Voice.isNetworkConnectionRequired() == false`، وألا يتضمن `KEY_FEATURE_NOT_INSTALLED`، وأن تكون بياناته مثبتة محليًا.
5. Android لا يوفّر حقل Gender موحدًا في Voice. لا يُستنتج Male من Pitch أو اسم اللغة. يعتمد التطبيق قائمة سماح موثقة مرتبطة بالمحرك وإصداره وVoice ID. معرّفات Google التي ظهرت كمرشحات ذكورية في الدراسة السابقة `ar-xa-x-ard-` و`ar-xa-x-are-` تبقى مرشحات خاصة بذلك المحرك، ويجب إثبات المعرّفات الفعلية على الجهاز قبل اعتمادها؛ لا تُعامل كمعيار Android عامًا.
6. إذا لم يوجد صوت Male عربي مثبت ومحلي ومطابق، تفشل العملية برسالة واضحة بدل اختيار صوت أنثوي أو مجهول أو شبكي.
7. اللغة الأساسية هي `ar-EG`. قاموس `ar-SA` اختياري، منفصل تمامًا، ولا يضاف إلا إذا أثبت قياس APK والذاكرة أنه صغير واجتاز اختبار عدم تغيير أي ناتج مصري. لا يُسمح بتطبيق قواعد سعودي على النص المصري.
8. القاموس طبقة تطبيع نصية مضمنة في Assets، وليس نموذج TTS أو Voice Model. يشمل عند الحاجة الأرقام والأسماء والاختصارات والتعبيرات المصرية والتشكيل والوقف، مع قواعد سياقية وإصدارات وبصمات.

### الترتيب الصحيح قبل التنفيذ

لا يبدأ TTS إلا بعد:
- إنهاء إصلاحات المدة والتوقيت وPTS والـmuxer.
- تثبيت إصلاح الرندر والـFallback وسياسة صورة المستخدم.
- إنهاء تخطيط اللقطات والاستمرارية.
- فض التعارضات وتوحيد أساس Phase/Stage مع الاحتفاظ بمزايا Phase الفريدة وضوابط Stage الآمنة، ثم اجتياز Regression على قاعدة موحدة.
- تثبيت عقد الصوت البشري وTimeline وMixer وTranscoder ونجاح اختبارات الصوت الأساسية، ثم إكمال Audio Mastering.

بعد ذلك ينفذ WOLF-OFFLINE-TTS-001 كمرحلة منفصلة، وقبل اختبارات القصص المرجعية النهائية واختبار الهاتف الفعلي واعتماد Release. هذه تبعية هندسية وليست إذنًا ببدء تلك المرحلة.

### عقد البيانات والتكامل والاختبارات

- يميز سجل الصوت `recording` عن `android_tts`، ويحفظ اللغة المطلوبة والفعلية والمحرك وإصداره وVoice ID وعلم الشبكة ونسخة/بصمة القاموس ومدة الملف وبصمة النص وحالة الاعتماد.
- إذا غاب `source` عن JSON قديم، يفسر على أنه تسجيل بشري للحفاظ على التوافق.
- يمر خرج TTS بعد فحص الصيغة وتحويله إلى AAC إلى خط الإنتاج الحالي؛ لا يمر WAV مباشرة إلى muxer إذا كان يشترط AAC.
- تشمل الاختبارات وضع الطيران، والتوليد بعد إعادة تشغيل التطبيق، والتقسيم عند حد طول TTS، وcallbacks، وفشل المحرك والتنظيف، وعزل القاموسين، وتوافق JSON القديم، ومزج الصوت والمقدمة والفصول، وصحة Audio Track في MP4، وعدم كسر التسجيل اليدوي.

### حدود التغيير في هذه الخطوة

- حُفظت الدراسة الأصلية وأضيفت مواصفة التنفيذ إلى فرع التوثيق.
- لم يُنشأ أي ملف Kotlin للميزة، ولم يتغير Manifest أو Gradle أو مخطط بيانات التطبيق.
- لم يبدأ تنفيذ TTS ولم يُبنَ APK أو يُجرَ اختبار على الهاتف.
- لا تمثل المواصفة ادعاءً بأن صوت Google أو بياناته مثبتة على كل جهاز.

