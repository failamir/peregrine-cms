module.exports = {
    convert: function($, f) {
    	f.wrap($, 'themeclean-components-block')
        f.bindAttribute($.parent(),'model','model')

        //Container
        let justifyClasses = `{
            'justify-content-start': model.aligncontent == 'left',
            'justify-content-center': model.aligncontent == 'center',
            'justify-content-end': model.aligncontent == 'right',
            'flex-column': model.layoutdirection == 'vertical',
            'flex-row': model.layoutdirectioon == 'horizontal'
        }`

        f.bindAttribute( $, 'class', justifyClasses ,false)

        //Text
        let textDiv = $.find('div').eq(0)
        f.bindAttribute( textDiv, 'class', "`text-${model.aligncontent}`",false)
        f.addStyle( textDiv, 'flex-basis', 'model.textwidth', '%')
    	f.addIf($.find('h2').first(), 'model.showtitle == \'true\'')
        f.addIf($.find('h4').first(), 'model.showsubtitle == \'true\'')
        f.addIf($.find('p').first(), 'model.showtext == \'true\'')
        f.mapRichField($.find('h2').first(), "model.title")
        f.mapRichField($.find('h4').first(), "model.subtitle")
        f.mapRichField($.find('p').first(), "model.text")

        //Buttons
        let buttonsDiv = $.find('div').eq(1)
        let link = buttonsDiv.find('a')
        f.addIf( buttonsDiv, 'model.showbutton == \'true\'')
        f.bindAttribute( buttonsDiv, 'class', '`justify-content-sm-${model.alignbuttons}`',false)
        f.addFor( link, 'model.buttons')
        f.bindAttribute( link, 'href', f.pathToUrl('item.buttonlink'))
        f.mapRichField( link, "item.buttontext")
        f.addStyle( link, 'backgroundColor', 'item.buttoncolor')
        f.addStyle( link, 'borderColor', 'item.buttoncolor')
        f.addStyle( link, 'margin-left', "i == 0 ? 0 : '0.5rem'")
        f.addStyle( link, 'margin-right', "i == model.buttons.length-1 ? 0: '0.5rem'")

        $.append('<div v-if="isEditAndEmpty">no content defined for component</div>')
    }
}